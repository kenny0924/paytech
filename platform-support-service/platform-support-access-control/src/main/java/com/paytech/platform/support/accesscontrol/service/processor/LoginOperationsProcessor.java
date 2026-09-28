package com.paytech.platform.support.accesscontrol.service.processor;

import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.core.configuration.OperationsAuthenticationProperties;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsAccount;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsMember;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsPassword;
import com.paytech.platform.support.accesscontrol.domain.authentication.LoginOperationsContext;
import com.paytech.platform.support.accesscontrol.domain.authentication.OperationsAuthenticationModels.LoginCommand;
import com.paytech.platform.support.accesscontrol.domain.authentication.OperationsAuthenticationModels.PreparedLogin;
import com.paytech.platform.support.accesscontrol.domain.authentication.OperationsAuthenticationModels.TokenResult;
import com.paytech.platform.support.accesscontrol.domain.enums.LoginFailureReason;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsAccountRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsMemberRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsPasswordRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsAuthenticationService;
import com.paytech.platform.support.accesscontrol.service.processor.request.OperationsLoginRequest;
import com.paytech.platform.support.api.enums.OperationsAccountType;
import com.paytech.platform.support.api.enums.OperationsErrorCode;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Identity;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Target;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.Outcome;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;

import lombok.AllArgsConstructor;

/** 处理 Operations 登录，失败计数提交后再拒绝。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class LoginOperationsProcessor extends AbstractLockProcessor<OperationsLoginRequest, TokenResult> {
    private final OperationsAuditService audit;
    private final OperationsMemberRepository members;
    private final OperationsAccountRepository accounts;
    private final OperationsAuthenticationProperties properties;
    private final OperationsPasswordRepository credentials;
    private final OperationsAuthenticationPreparation preparation;
    private final OperationsAuthenticationService authentication;
    private final PlatformTransactionManager transactionManager;

    /** 事务外解析真实锁资源，供锁参数和锁内业务共同使用。 */
    @Override
    protected TokenResult processRequest(OperationsLoginRequest request) {
        var resolution =
            preparation.resolveLogin(new LoginCommand(request.getLoginIdentifier(), request.getPassword()));
        request = new OperationsLoginRequest(request.getLoginIdentifier(), request.getPassword(), resolution);
        return super.processRequest(request);
    }

    /** 同资源入口共用实际 Key；模板统一添加 lock 前缀。 */
    @Override
    protected LockCommand lockCommand(OperationsLoginRequest request) {
        return LockCommand.tryLock(
            (request.getResolution().accountId() == null ? "operations:login-attempt:" : "operations:account:")
                + request.getResolution().resourceKey(),
            Math.toIntExact(properties.getLockWaitTime().toMillis()),
            Math.toIntExact(properties.getLockLeaseTime().toMillis()));
    }

    /** 密码和签名在事务外，提交时按原安全快照复核。 */
    @Override
    protected TokenResult processWithinLock(OperationsLoginRequest request) {
        if (request.getResolution().accountId() == null) {
            rejectUnknownLogin(request);
        }
        var prepared = preparation.prepareLogin(request.getResolution().accountId(),
            new LoginCommand(request.getLoginIdentifier(), request.getPassword()));
        TokenResult result = transaction().execute(status -> {
            audit.observeTransaction();
            return commitLogin(prepared);
        });
        if (result == null) {
            throw new FailException(OperationsErrorCode.OPERATIONS_AUTHENTICATION_FAILED);
        }
        return result;
    }

    /** 未知账号执行等价密码计算，在受控尝试桶内统一拒绝。 */
    void rejectUnknownLogin(OperationsLoginRequest request) {
        preparation.rejectUnknownLogin(request.getPassword());
        recordDenied(null, LoginFailureReason.UNKNOWN_ACCOUNT, preparation.now());
        throw new FailException(OperationsErrorCode.OPERATIONS_AUTHENTICATION_FAILED);
    }

    /** 接受当前安全事实后构造完整上下文并原子保存。 */
    TokenResult commitLogin(PreparedLogin prepared) {
        Instant now = preparation.now();
        if (prepared.failureReason() != null) {
            if (prepared.failureReason() == LoginFailureReason.PASSWORD_MISMATCH && prepared.account() != null) {
                authentication.passwordFailure(prepared.account(), now);
            }
            recordDenied(prepared.account(), prepared.failureReason(), now);
            return null;
        }
        OperationsAccount account = accounts.account(prepared.account().id());
        OperationsPassword password = credentials.password(prepared.account().id());
        OperationsMember member =
            prepared.account().type() == OperationsAccountType.HUMAN ? members.member(prepared.account().id()) : null;
        if (!now.isBefore(prepared.preparedAt().plusSeconds(900))
            || !preparation.sameLoginSnapshot(prepared, account, password, member, now)) {
            throw preparation.concurrentLogin();
        }
        var context = new LoginOperationsContext(account, password, member,
            preparation.committedSession(prepared.session(), now), prepared.tokens(), now);
        audit.identify(() -> new Identity(Long.toString(account.id()), member == null ? null : member.tenantId()));
        audit.target(() -> new Target("USER_ACCOUNT", Long.toString(account.id())));
        authentication.login(context);
        audit.record(() -> Outcome.succeeded(Long.toString(account.id()), context.getSession().tenantId(),
            new Target("AUTH_SESSION", Long.toString(context.getSession().id())), "PASSWORD_ACCEPTED", now));
        return context.getTokens().response();
    }

    /** 冻结安全拒绝原因，不记录输入标识或密码。 */
    void recordDenied(OperationsAccount account, LoginFailureReason reason, Instant now) {
        String actor = account == null ? "UNKNOWN" : Long.toString(account.id());
        audit.record(() -> Outcome.denied(actor, null, new Target("USER_ACCOUNT", actor), reason.getCode(), now));
    }

    /** 固定本地写事务范围；审计只观察提交，不阻断主业务。 */
    TransactionTemplate transaction() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(15);
        return transaction;
    }
}
