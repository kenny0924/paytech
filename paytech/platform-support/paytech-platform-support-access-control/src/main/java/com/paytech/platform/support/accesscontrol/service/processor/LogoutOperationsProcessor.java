package com.paytech.platform.support.accesscontrol.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.core.configuration.OperationsAuthenticationProperties;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsRefresh;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsSession;
import com.paytech.platform.support.accesscontrol.domain.authentication.LogoutOperationsContext;
import com.paytech.platform.support.accesscontrol.domain.authentication.OperationsAuthenticationModels.PreparedLogout;
import com.paytech.platform.support.accesscontrol.domain.authentication.OperationsAuthenticationModels.RefreshCommand;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsRefreshRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsSessionRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsAuthenticationService;
import com.paytech.platform.support.accesscontrol.service.processor.request.OperationsLogoutRequest;
import com.paytech.platform.support.api.enums.OperationsErrorCode;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Identity;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Target;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.Outcome;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;

import lombok.AllArgsConstructor;

/** 证明会话归属后撤销当前会话。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class LogoutOperationsProcessor extends AbstractLockProcessor<OperationsLogoutRequest, Void> {
    private final OperationsAuditService audit;
    private final OperationsSessionRepository sessions;
    private final OperationsRefreshRepository refreshes;
    private final OperationsAuthenticationProperties properties;
    private final OperationsAuthenticationPreparation preparation;
    private final OperationsAuthenticationService authentication;
    private final PlatformTransactionManager transactionManager;

    /** 同资源入口共用实际 Key；模板统一添加 lock 前缀。 */
    @Override
    protected LockCommand lockCommand(OperationsLogoutRequest request) {
        return LockCommand.tryLock("operations:session:" + request.getSessionId(),
            Math.toIntExact(properties.getLockWaitTime().toMillis()),
            Math.toIntExact(properties.getLockLeaseTime().toMillis()));
    }

    /** 持有刷新秘密才可退出；数据库重新检查同一会话归属。 */
    @Override
    protected Void processWithinLock(OperationsLogoutRequest request) {
        var prepared = preparation.prepareLogout(Long.toString(request.getAccountId()),
            Long.toString(request.getSessionId()), new RefreshCommand(request.getRefreshToken()));
        transaction().executeWithoutResult(status -> {
            audit.observeTransaction();
            var context = currentContext(prepared);
            var session = context.getSession();
            audit.identify(() -> new Identity(Long.toString(session.accountId()), session.tenantId()));
            audit.target(() -> new Target("AUTH_SESSION", Long.toString(session.id())));
            authentication.logout(context);
            audit.record(() -> Outcome.succeeded(Long.toString(session.accountId()), session.tenantId(),
                new Target("AUTH_SESSION", Long.toString(session.id())), "USER_LOGOUT", prepared.preparedAt()));
        });
        return null;
    }

    /** 会话行锁后核对原证明，保留终态退出行为。 */
    LogoutOperationsContext currentContext(PreparedLogout prepared) {
        OperationsSession session = sessions.lockSession(prepared.session().id());
        OperationsRefresh refresh = refreshes.refresh(prepared.refresh().lookupId());
        if (session == null || session.accountId() != prepared.accountId() || refresh == null
            || refresh.sessionId() != session.id() || !preparation.sameRefreshSnapshot(prepared.refresh(), refresh)) {
            throw new FailException(OperationsErrorCode.OPERATIONS_SESSION_INVALID);
        }
        return new LogoutOperationsContext(session, refresh, preparation.now());
    }

    /** 固定本地写事务范围；审计只观察提交，不阻断主业务。 */
    TransactionTemplate transaction() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(15);
        return transaction;
    }
}
