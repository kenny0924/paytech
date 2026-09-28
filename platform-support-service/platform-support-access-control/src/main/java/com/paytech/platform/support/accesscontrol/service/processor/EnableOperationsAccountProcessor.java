package com.paytech.platform.support.accesscontrol.service.processor;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.core.configuration.OperationsAuthenticationProperties;
import com.paytech.platform.support.accesscontrol.domain.operations.OperationsAccountStateContext;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsDirectoryRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsAccountService;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.accesscontrol.service.processor.request.EnableOperationsAccountRequest;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.AccountEnabled;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.AccountState;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;

import lombok.AllArgsConstructor;

/** EnableOperationsAccount 用例：授权、事实校验及本地事务编排。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class EnableOperationsAccountProcessor extends AbstractLockProcessor<EnableOperationsAccountRequest, Void> {
    private final Clock clock;
    private final OperationsAuditService audit;
    private final OperationsAccountService accounts;
    private final OperationsManagementSupport support;
    private final OperationsDirectoryRepository directory;
    private final OperationsManagementTransactions transactions;
    private final OperationsManagementPolicyService policy;
    private final OperationsAuthenticationProperties properties;

    /** 同资源命令共用 Key，模板统一负责锁及公共 Redis 异常。 */
    @Override
    protected LockCommand lockCommand(EnableOperationsAccountRequest request) {
        return LockCommand.tryLock("operations:account:" + request.getAccountId(),
            Math.toIntExact(properties.getLockWaitTime().toMillis()),
            Math.toIntExact(properties.getLockLeaseTime().toMillis()));
    }

    /** 本地写入在同一事务内完成；外部准备已在事务前结束。 */
    @Override
    protected Void processWithinLock(EnableOperationsAccountRequest request) {
        return transactions.write(() -> {
            var actor = policy.authorizeManagement(request.getActor(), request.getSession(), request.getAccountId(),
                List.of(), null, null, false, "OPERATIONS_ACCOUNT_ENABLE");
            var target = support.requireHuman(request.getAccountId());
            var member = support.requireEnableableMember(target, actor.member().tenantId());
            policy.requireGrantableRoles(actor, directory.activeMemberRoleIds(member.id()));
            support.requireVersion(target.version(), request.getExpectedVersion());
            var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
            var oldStatus = target.status().getCode();
            int oldVersion = target.version(), oldEpoch = target.authEpoch();
            var context = new OperationsAccountStateContext(target, member, now);
            accounts.enableAccount(context);
            audit.record(() -> new AccountEnabled(request.getActor(), member.tenantId(),
                new AccountState(Long.toString(target.id()), oldStatus, oldVersion, oldEpoch),
                Long.toString(member.id()), request.getReason(), now));
            return null;
        });
    }
}
