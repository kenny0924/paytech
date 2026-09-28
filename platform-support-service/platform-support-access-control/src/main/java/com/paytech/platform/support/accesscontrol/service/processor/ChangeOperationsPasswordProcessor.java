package com.paytech.platform.support.accesscontrol.service.processor;

import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.core.configuration.OperationsAuthenticationProperties;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsAccount;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsMember;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsPassword;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsSession;
import com.paytech.platform.support.accesscontrol.domain.authentication.ChangeOperationsPasswordContext;
import com.paytech.platform.support.accesscontrol.domain.authentication.OperationsAuthenticationModels.ChangePasswordCommand;
import com.paytech.platform.support.accesscontrol.domain.authentication.OperationsAuthenticationModels.PreparedPasswordChange;
import com.paytech.platform.support.accesscontrol.domain.authentication.OperationsSessionPolicy;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsAccountRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsMemberRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsPasswordRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsSessionRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsAuthenticationService;
import com.paytech.platform.support.accesscontrol.service.processor.request.OperationsPasswordRequest;
import com.paytech.platform.support.api.enums.OperationsAccountType;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Identity;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Target;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.Outcome;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;

import lombok.AllArgsConstructor;

/** 更换本人密码并撤销账号全部会话。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ChangeOperationsPasswordProcessor extends AbstractLockProcessor<OperationsPasswordRequest, Void> {
    private final OperationsAuditService audit;
    private final OperationsMemberRepository members;
    private final OperationsAccountRepository accounts;
    private final OperationsSessionRepository sessions;
    private final OperationsAuthenticationProperties properties;
    private final OperationsPasswordRepository credentials;
    private final OperationsAuthenticationPreparation preparation;
    private final OperationsAuthenticationService authentication;
    private final PlatformTransactionManager transactionManager;

    /** 同资源入口共用实际 Key；模板统一添加 lock 前缀。 */
    @Override
    protected LockCommand lockCommand(OperationsPasswordRequest request) {
        return LockCommand.tryLock("operations:account:" + request.getAccountId(),
            Math.toIntExact(properties.getLockWaitTime().toMillis()),
            Math.toIntExact(properties.getLockLeaseTime().toMillis()));
    }

    /** 密码计算和 Vault Reveal 在事务外，提交后要求重新登录。 */
    @Override
    protected Void processWithinLock(OperationsPasswordRequest request) {
        var prepared = preparation.preparePasswordChange(Long.toString(request.getAccountId()),
            Long.toString(request.getSessionId()),
            new ChangePasswordCommand(request.getCurrentPassword(), request.getNewPassword()));
        transaction().executeWithoutResult(status -> {
            audit.observeTransaction();
            var context = currentContext(prepared);
            audit.identify(
                () -> new Identity(Long.toString(context.getAccount().id()), context.getSession().tenantId()));
            audit.target(() -> new Target("USER_ACCOUNT", Long.toString(context.getAccount().id())));
            authentication.changePassword(context);
            audit.record(() -> Outcome.succeeded(Long.toString(context.getAccount().id()),
                context.getSession().tenantId(), new Target("USER_ACCOUNT", Long.toString(context.getAccount().id())),
                "PASSWORD_CHANGED", context.getNow()));
        });
        return null;
    }

    /** 当前事实必须仍匹配准备阶段，写入阶段另以条件 DML 最终保护。 */
    ChangeOperationsPasswordContext currentContext(PreparedPasswordChange prepared) {
        Instant now = preparation.now();
        OperationsAccount account = accounts.account(prepared.account().id());
        OperationsPassword password = credentials.password(prepared.account().id());
        OperationsSession session = sessions.session(prepared.session().id());
        OperationsMember member = account == null || account.type() == OperationsAccountType.BOOTSTRAP_ADMIN ? null
            : members.member(account.id());
        if (!preparation.samePasswordChangeSnapshot(prepared, account, password, session)) {
            throw preparation.concurrentPasswordChange();
        }
        OperationsSessionPolicy.requireValid(account, password, session, member, now);
        return new ChangeOperationsPasswordContext(account, password, session, prepared.nextVerifier(), now);
    }

    /** 固定本地写事务范围；审计只观察提交，不阻断主业务。 */
    TransactionTemplate transaction() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(15);
        return transaction;
    }
}
