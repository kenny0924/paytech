package com.paytech.platform.support.accesscontrol.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.core.configuration.ClientAuthenticationProperties;
import com.paytech.platform.support.accesscontrol.domain.authentication.ClientAuthenticationPlans.RefreshPlan;
import com.paytech.platform.support.accesscontrol.domain.authentication.ClientAuthenticationPlans.Tokens;
import com.paytech.platform.support.accesscontrol.domain.authentication.RefreshClientContext;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsRefreshStatus;
import com.paytech.platform.support.accesscontrol.domainservice.service.CustomerAuthenticationService;
import com.paytech.platform.support.accesscontrol.service.processor.request.ClientRefreshRequest;
import com.paytech.platform.support.api.enums.ClientAuthenticationErrorCode;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Identity;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Result;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Target;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.CustomerAuthenticationOutcome;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;

import lombok.AllArgsConstructor;

/** 轮换 Client 刷新令牌并检测旧代重用。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "client.authentication.enabled", havingValue = "true")
public class RefreshClientSessionProcessor extends AbstractLockProcessor<ClientRefreshRequest, Tokens> {
    private final OperationsAuditService audit;
    private final ClientSessionPreparation preparation;
    private final PlatformTransactionManager transactionManager;
    private final CustomerAuthenticationService authentication;
    private final ClientAuthenticationProperties properties;

    /** 在取锁前定位真实会话；公开查找段不作为认证证明。 */
    @Override
    protected Tokens processRequest(ClientRefreshRequest request) {
        var identity = preparation.resolveRefresh(request.getRefreshToken());
        if (identity == null) {
            throw new FailException(ClientAuthenticationErrorCode.CLIENT_SESSION_INVALID);
        }
        return super.processRequest(new ClientRefreshRequest(request.getRefreshToken(), identity));
    }

    /** 使用已解析的真实资源；同一资源的入口共用 Key。 */
    @Override
    protected LockCommand lockCommand(ClientRefreshRequest request) {
        return LockCommand.tryLock("customer:session:" + request.getIdentity().sessionId(),
            Math.toIntExact(properties.getLockWaitTime().toMillis()),
            Math.toIntExact(properties.getLockLeaseTime().toMillis()));
    }

    /** 正确旧代重用先提交撤销，再返回失效；新令牌只在轮换事务提交后返回。 */
    @Override
    protected Tokens processWithinLock(ClientRefreshRequest request) {
        RefreshPlan plan = preparation.prepareRefresh(request.getIdentity(), request.getRefreshToken());
        Tokens result = transaction().execute(status -> {
            audit.observeTransaction();
            var context = preparation.currentRefresh(plan);
            if (context == null) {
                return null;
            }
            if (context.getRefresh().status() == OperationsRefreshStatus.CONSUMED) {
                authentication.compromise(context);
                recordOutcome(context, Result.DENIED, "REFRESH_REUSE", true);
                return null;
            }
            if (!preparation.validRefresh(context)) {
                authentication.invalidate(context);
                recordOutcome(context, Result.DENIED, "SESSION_INVALID", true);
                return null;
            }
            var session = context.getSession();
            var refresh = context.getRefresh();
            if (refresh.status() != OperationsRefreshStatus.ISSUED || refresh.generationNo() != session.generationNo()
                || !context.getNow().isBefore(refresh.expiresTime()) || plan.issue() == null
                || session.version() != plan.expectedSessionVersion()) {
                return null;
            }
            preparation.requireCurrentSigningKey(plan.issue(), context.getNow());
            authentication.refresh(context);
            recordOutcome(context, Result.SUCCEEDED, "REFRESH_ROTATED", false);
            return plan.issue().tokens();
        });
        if (result == null) {
            throw new FailException(ClientAuthenticationErrorCode.CLIENT_SESSION_INVALID);
        }
        return result;
    }

    /** 保留刷新成功及安全撤销的审计原因。 */
    void recordOutcome(RefreshClientContext context, Result result, String reason, boolean revoked) {
        audit
            .record(() -> new CustomerAuthenticationOutcome(
                new Identity(result == Result.SUCCEEDED ? Long.toString(context.getPlan().accountId()) : "UNKNOWN",
                    null),
                new Target("AUTH_SESSION", Long.toString(context.getSession().id())), result, reason, context.getNow(),
                null, null, false, false, revoked));
    }

    /** 本库写事务保持读已提交和十五秒超时；审计观察实际提交结果。 */
    TransactionTemplate transaction() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(15);
        return transaction;
    }
}
