package com.paytech.platform.support.accesscontrol.service.processor;

import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.core.configuration.OperationsAuthenticationProperties;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.*;
import com.paytech.platform.support.accesscontrol.domain.authentication.OperationsAuthenticationModels.PreparedRefresh;
import com.paytech.platform.support.accesscontrol.domain.authentication.OperationsAuthenticationModels.RefreshCommand;
import com.paytech.platform.support.accesscontrol.domain.authentication.OperationsAuthenticationModels.TokenResult;
import com.paytech.platform.support.accesscontrol.domain.authentication.OperationsSessionPolicy;
import com.paytech.platform.support.accesscontrol.domain.authentication.RefreshOperationsContext;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsRefreshStatus;
import com.paytech.platform.support.accesscontrol.domainservice.repository.*;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsAuthenticationService;
import com.paytech.platform.support.accesscontrol.service.processor.request.OperationsRefreshRequest;
import com.paytech.platform.support.api.enums.OperationsAccountType;
import com.paytech.platform.support.api.enums.OperationsErrorCode;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Identity;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Target;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.Outcome;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;

import lombok.AllArgsConstructor;

/** 单次轮换刷新代，正确旧代重用先撤销再拒绝。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class RefreshOperationsSessionProcessor extends AbstractLockProcessor<OperationsRefreshRequest, TokenResult> {
    private final OperationsAuditService audit;
    private final OperationsMemberRepository members;
    private final OperationsAccountRepository accounts;
    private final OperationsSessionRepository sessions;
    private final OperationsRefreshRepository refreshes;
    private final OperationsAuthenticationProperties properties;
    private final OperationsPasswordRepository credentials;
    private final OperationsAuthenticationPreparation preparation;
    private final OperationsAuthenticationService authentication;
    private final PlatformTransactionManager transactionManager;

    /** 事务外解析真实锁资源，供锁参数和锁内业务共同使用。 */
    @Override
    protected TokenResult processRequest(OperationsRefreshRequest request) {
        var resolution = preparation.resolveRefresh(new RefreshCommand(request.getRefreshToken()));
        request = new OperationsRefreshRequest(request.getRefreshToken(), resolution);
        return super.processRequest(request);
    }

    /** 同资源入口共用实际 Key；模板统一添加 lock 前缀。 */
    @Override
    protected LockCommand lockCommand(OperationsRefreshRequest request) {
        return LockCommand.tryLock(
            (request.getResolution().sessionId() == null ? "operations:refresh-attempt:" : "operations:session:")
                + request.getResolution().resourceKey(),
            Math.toIntExact(properties.getLockWaitTime().toMillis()),
            Math.toIntExact(properties.getLockLeaseTime().toMillis()));
    }

    /** HMAC 和预签在事务外；重用或失效处置提交后再返回 401。 */
    @Override
    protected TokenResult processWithinLock(OperationsRefreshRequest request) {
        var prepared = request.getResolution().sessionId() == null ? null : preparation
            .prepareRefresh(request.getResolution().sessionId(), new RefreshCommand(request.getRefreshToken()));
        TokenResult result = prepared == null ? denied(null, "REFRESH_REJECTED") : transaction().execute(status -> {
            audit.observeTransaction();
            return commitRefresh(prepared);
        });
        if (result == null) {
            throw new FailException(OperationsErrorCode.OPERATIONS_SESSION_INVALID);
        }
        return result;
    }

    /** 保留 Session X 首先仲裁，再复核刷新秘密和当前账号资格的顺序。 */
    TokenResult commitRefresh(PreparedRefresh prepared) {
        OperationsSession session = sessions.lockSession(prepared.refresh().sessionId());
        Instant now = preparation.now();
        if (session == null) {
            return denied(null, "REFRESH_REJECTED");
        }
        audit.identify(() -> new Identity(Long.toString(session.accountId()), session.tenantId()));
        audit.target(() -> new Target("AUTH_SESSION", Long.toString(session.id())));
        OperationsRefresh refresh = refreshes.refresh(prepared.refresh().lookupId());
        OperationsAccount account = accounts.account(session.accountId());
        OperationsPassword password = account == null ? null : credentials.password(account.id());
        OperationsMember member = account == null || account.type() == OperationsAccountType.BOOTSTRAP_ADMIN ? null
            : members.member(account.id());
        if (!preparation.sameRefreshSnapshot(prepared.refresh(), refresh) || refresh.sessionId() != session.id()) {
            return denied(session, "REFRESH_REJECTED");
        }
        var context = new RefreshOperationsContext(account, password, member, session, refresh, prepared.tokens(), now);
        if (refresh.status() == OperationsRefreshStatus.CONSUMED) {
            authentication.compromise(context);
            return denied(session, "REFRESH_REUSE");
        }
        if (refresh.status() != OperationsRefreshStatus.ISSUED || !now.isBefore(refresh.expiresTime())) {
            return denied(session, "REFRESH_REJECTED");
        }
        return rotate(context, prepared);
    }

    /** 只有原安全快照仍有效时保存唯一后继。 */
    TokenResult rotate(RefreshOperationsContext context, PreparedRefresh prepared) {
        var session = context.getSession();
        try {
            OperationsSessionPolicy.requireValid(context.getAccount(), context.getPassword(), session,
                context.getMember(), context.getNow());
        } catch (FailException failure) {
            authentication.invalidate(context);
            return denied(session, "REFRESH_REJECTED");
        }
        if (context.getRefresh().generationNo() != session.generationNo()) {
            throw new ErrorException("Refresh 当前代号不一致");
        }
        if (context.getTokens() == null || !preparation.sameRefreshSecuritySnapshot(prepared, session,
            context.getAccount(), context.getPassword(), context.getMember())) {
            throw preparation.concurrentRefresh();
        }
        authentication.refresh(context);
        audit.record(() -> Outcome.succeeded(Long.toString(session.accountId()), session.tenantId(),
            new Target("AUTH_SESSION", Long.toString(session.id())), "GENERATION_ROTATED", context.getNow()));
        return context.getTokens().response();
    }

    /** 返回空结果，使已登记的撤销事务先提交，外层再报告失效。 */
    TokenResult denied(OperationsSession session, String reason) {
        String actor = session == null ? "UNKNOWN" : Long.toString(session.accountId());
        String id = session == null ? "UNKNOWN" : Long.toString(session.id());
        audit.record(() -> Outcome.denied(actor, session == null ? null : session.tenantId(),
            new Target("AUTH_SESSION", id), reason, preparation.now()));
        return null;
    }

    /** 固定本地写事务范围；审计只观察提交，不阻断主业务。 */
    TransactionTemplate transaction() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(15);
        return transaction;
    }
}
