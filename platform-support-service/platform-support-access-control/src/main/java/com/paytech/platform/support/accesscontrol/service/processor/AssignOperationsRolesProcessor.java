package com.paytech.platform.support.accesscontrol.service.processor;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.core.configuration.OperationsAuthenticationProperties;
import com.paytech.platform.support.accesscontrol.domain.operations.AssignOperationsRolesContext;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsAccountService;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.accesscontrol.service.processor.request.AssignOperationsRolesRequest;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Target;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.Outcome;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;

import lombok.AllArgsConstructor;

/** AssignOperationsRoles 用例：授权、事实校验及本地事务编排。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class AssignOperationsRolesProcessor extends AbstractLockProcessor<AssignOperationsRolesRequest, Void> {
    private final Clock clock;
    private final OperationsAuditService audit;
    private final OperationsAccountService accounts;
    private final OperationsManagementSupport support;
    private final OperationsManagementTransactions transactions;
    private final OperationsManagementPolicyService policy;
    private final OperationsAuthenticationProperties properties;

    /** 同资源命令共用 Key，模板统一负责锁及公共 Redis 异常。 */
    @Override
    protected LockCommand lockCommand(AssignOperationsRolesRequest request) {
        return LockCommand.tryLock("operations:account:" + request.getAccountId(),
            Math.toIntExact(properties.getLockWaitTime().toMillis()),
            Math.toIntExact(properties.getLockLeaseTime().toMillis()));
    }

    /** 本地写入在同一事务内完成；外部准备已在事务前结束。 */
    @Override
    protected Void processWithinLock(AssignOperationsRolesRequest request) {
        return transactions.write(() -> {
            var actor = policy.authorizeManagement(request.getActor(), request.getSession(), request.getAccountId(),
                request.getRoleIds(), null, null, true, "OPERATIONS_ACCOUNT_ASSIGN_ROLES");
            var target = support.requireHuman(request.getAccountId());
            var member = support.requireMember(target.id(), actor.member().tenantId());
            var roleIds = policy.validateRoles(request.getRoleIds());
            policy.requireGrantableRoles(actor, roleIds);
            policy.protectLastAdministrator(actor, target.id(), roleIds);
            support.requireVersion(member.version(), request.getExpectedMemberVersion());
            var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
            var context =
                new AssignOperationsRolesContext(target, member, support.roles(roleIds), actor.account().id(), now);
            accounts.assignRoles(context);
            audit.record(() -> Outcome.succeeded(request.getActor(), member.tenantId(),
                new Target("USER_ACCOUNT", Long.toString(target.id())), request.getReason(), now));
            return null;
        });
    }
}
