package com.paytech.platform.support.accesscontrol.service.processor;

import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.core.configuration.ClientAuthenticationProperties;
import com.paytech.platform.support.accesscontrol.domain.authentication.ClientAuthenticationPlans.LogoutProof;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsSessionStatus;
import com.paytech.platform.support.accesscontrol.domainservice.service.CustomerAuthenticationService;
import com.paytech.platform.support.accesscontrol.service.processor.request.ClientLogoutRequest;
import com.paytech.platform.support.api.enums.ClientAuthenticationErrorCode;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Identity;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Target;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Result;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.CustomerAuthenticationOutcome;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;
import lombok.AllArgsConstructor;

/** 撤销 Client 当前会话。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "client.authentication.enabled", havingValue = "true")
public class LogoutClientProcessor extends AbstractLockProcessor<ClientLogoutRequest, Void> {
    private final OperationsAuditService audit;
    private final ClientSessionPreparation preparation;
    private final PlatformTransactionManager transactionManager;
    private final CustomerAuthenticationService authentication;
    private final ClientAuthenticationProperties properties;

    /** 使用已解析的真实资源；同一资源的入口共用 Key。 */
    @Override
    protected LockCommand lockCommand(ClientLogoutRequest request) {
        return LockCommand.tryLock("customer:session:" + request.getSessionId(), Math.toIntExact(properties.getLockWaitTime().toMillis()),
            Math.toIntExact(properties.getLockLeaseTime().toMillis()));
    }

    /** 事务外计算秘密证明；当前 Session 与 Refresh 在同一本地事务终止。 */
    @Override
    protected Void processWithinLock(ClientLogoutRequest request) {
        LogoutProof proof = preparation.prepareLogout(request.getAccountId(), request.getSessionId(), request.getRefreshToken());
        boolean accepted = Boolean.TRUE.equals(transaction().execute(status -> {
            audit.observeTransaction();
            var context = preparation.currentLogout(proof);
            if (context == null) { return false; }
            boolean revoked = context.getSession().status() == OperationsSessionStatus.ACTIVE;
            authentication.logout(context);
            audit.record(() -> new CustomerAuthenticationOutcome(new Identity(Long.toString(proof.accountId()), null),
                new Target("AUTH_SESSION", Long.toString(proof.sessionId())), Result.SUCCEEDED, "LOGOUT", context.getNow(),
                null, null, false, false, revoked));
            return true;
        }));
        if (!accepted) { throw new FailException(ClientAuthenticationErrorCode.CLIENT_SESSION_INVALID); }
        return null;
    }

    /** 本库写事务保持读已提交和十五秒超时；审计观察实际提交结果。 */
    TransactionTemplate transaction() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(15);
        return transaction;
    }
}
