package com.paytech.platform.support.accesscontrol.service.processor;

import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.core.configuration.ClientAuthenticationProperties;
import com.paytech.platform.support.accesscontrol.domain.authentication.LoginClientContext;
import com.paytech.platform.support.accesscontrol.domain.authentication.ClientAuthenticationPlans.Tokens;
import com.paytech.platform.support.accesscontrol.domain.authentication.ClientAuthenticationPlans.LoginPlan;
import com.paytech.platform.support.accesscontrol.domainservice.service.CustomerAuthenticationService;
import com.paytech.platform.support.accesscontrol.service.processor.request.ClientLoginRequest;
import com.paytech.platform.support.api.enums.ClientAuthenticationErrorCode;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Identity;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Target;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Result;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.CustomerAuthenticationOutcome;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;
import lombok.AllArgsConstructor;

/** 校验 Client 密码并签发账号会话。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "client.authentication.enabled", havingValue = "true")
public class LoginClientProcessor extends AbstractLockProcessor<ClientLoginRequest, Tokens> {
    private final OperationsAuditService audit;
    private final ClientSessionPreparation preparation;
    private final PlatformTransactionManager transactionManager;
    private final CustomerAuthenticationService authentication;
    private final ClientAuthenticationProperties properties;

    /** 未知账号同样执行密码计算；只对真实账号取得资源锁。 */
    @Override
    protected Tokens processRequest(ClientLoginRequest request) {
        Long accountId = preparation.resolveLogin(request.getEmail(), request.getPassword());
        if (accountId == null) { throw new FailException(ClientAuthenticationErrorCode.CLIENT_AUTHENTICATION_FAILED); }
        return super.processRequest(new ClientLoginRequest(request.getEmail(), request.getPassword(), accountId));
    }

    /** 使用已解析的真实资源；同一资源的入口共用 Key。 */
    @Override
    protected LockCommand lockCommand(ClientLoginRequest request) {
        return LockCommand.tryLock("customer:account:" + request.getAccountId(), Math.toIntExact(properties.getLockWaitTime().toMillis()),
            Math.toIntExact(properties.getLockLeaseTime().toMillis()));
    }

    /** 密码校验和签名在事务外；登录失败计数提交后才返回 401。 */
    @Override
    protected Tokens processWithinLock(ClientLoginRequest request) {
        LoginPlan plan = preparation.prepareLogin(request.getAccountId(), request.getPassword());
        Tokens result = transaction().execute(status -> {
            audit.observeTransaction();
            var context = preparation.currentLogin(plan);
            if (context == null) { return null; }
            if (plan.passwordMismatch()) {
                authentication.passwordFailure(context);
                recordOutcome(context, Result.DENIED, "PASSWORD_MISMATCH", null);
                return null;
            }
            if (plan.issue() == null || !preparation.eligibleLogin(context)) {
                recordOutcome(context, Result.DENIED, "ACCOUNT_NOT_ELIGIBLE", null);
                return null;
            }
            preparation.requireCurrentSigningKey(plan.issue(), context.getNow());
            authentication.login(context);
            recordOutcome(context, Result.SUCCEEDED, "LOGIN", plan.issue().sessionId());
            return plan.issue().tokens();
        });
        if (result == null) { throw new FailException(ClientAuthenticationErrorCode.CLIENT_AUTHENTICATION_FAILED); }
        return result;
    }

    /** 只冻结审计所需的安全标量，不传递凭据对象。 */
    void recordOutcome(LoginClientContext context, Result result, String reason, Long sessionId) {
        long accountId = context.getAccount().id();
        audit.record(() -> new CustomerAuthenticationOutcome(new Identity(result == Result.SUCCEEDED ? Long.toString(accountId) : "UNKNOWN", null),
            new Target(sessionId == null ? "USER_ACCOUNT" : "AUTH_SESSION", Long.toString(sessionId == null ? accountId : sessionId)),
            result, reason, context.getNow(), null, null, false, false, false));
    }

    /** 本库写事务保持读已提交和十五秒超时；审计观察实际提交结果。 */
    TransactionTemplate transaction() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(15);
        return transaction;
    }
}
