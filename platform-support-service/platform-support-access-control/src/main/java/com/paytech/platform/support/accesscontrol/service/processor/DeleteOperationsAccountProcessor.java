package com.paytech.platform.support.accesscontrol.service.processor;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.core.configuration.OperationsAuthenticationProperties;
import com.paytech.platform.support.accesscontrol.domain.operations.DeleteOperationsAccountContext;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsDirectoryRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsPasswordRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsAccountService;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.accesscontrol.service.processor.request.DeleteOperationsAccountRequest;
import com.paytech.platform.support.api.enums.OperationsAccountStatus;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.AccountDeleted;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.AccountState;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.MemberState;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;

import lombok.AllArgsConstructor;

/** DeleteOperationsAccount 用例：授权、事实校验及本地事务编排。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class DeleteOperationsAccountProcessor extends AbstractLockProcessor<DeleteOperationsAccountRequest, Void> {
    private final Clock clock;
    private final OperationsAuditService audit;
    private final OperationsAccountService accounts;
    private final OperationsManagementSupport support;
    private final OperationsPasswordRepository passwords;
    private final OperationsDirectoryRepository directory;
    private final OperationsManagementTransactions transactions;
    private final OperationsManagementPolicyService policy;
    private final OperationsAuthenticationProperties properties;

    /** 同资源命令共用 Key，模板统一负责锁及公共 Redis 异常。 */
    @Override
    protected LockCommand lockCommand(DeleteOperationsAccountRequest request) {
        return LockCommand.tryLock("operations:account:" + request.getAccountId(),
            Math.toIntExact(properties.getLockWaitTime().toMillis()),
            Math.toIntExact(properties.getLockLeaseTime().toMillis()));
    }

    /** 本地写入在同一事务内完成；外部准备已在事务前结束。 */
    @Override
    protected Void processWithinLock(DeleteOperationsAccountRequest request) {
        return transactions.write(() -> {
            var actor = policy.authorizeManagement(request.getActor(), request.getSession(), request.getAccountId(),
                List.of(), null, null, true, "OPERATIONS_ACCOUNT_DELETE");
            var target = support.requireHuman(request.getAccountId());
            var member = support.requireMember(target.id(), actor.member().tenantId());
            if (target.id() == actor.account().id()) {
                throw new BizCheckFailException("不能删除本人账号");
            }
            if (target.status() != OperationsAccountStatus.ACTIVE
                && target.status() != OperationsAccountStatus.DISABLED) {
                throw new BizCheckFailException("当前人员账号状态不允许删除");
            }
            support.requireVersion(target.version(), request.getExpectedVersion());
            support.requireVersion(member.version(), request.getExpectedMemberVersion());
            policy.protectLastAdministrator(actor, target.id(), List.of());
            var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
            var roleIds = directory.activeMemberRoleIds(member.id());
            var oldStatus = target.status().getCode();
            int oldVersion = target.version(), oldEpoch = target.authEpoch(), oldMemberVersion = member.version();
            var oldMemberStatus = member.status().getCode();
            var context = new DeleteOperationsAccountContext(target, member, passwords.password(target.id()), now);
            accounts.deleteAccount(context);
            audit.record(() -> new AccountDeleted(request.getActor(),
                new AccountState(Long.toString(target.id()), oldStatus, oldVersion, oldEpoch),
                new MemberState(Long.toString(member.id()), member.tenantId(), oldMemberStatus, oldMemberVersion),
                roleIds, request.getReason(), now));
            return null;
        });
    }
}
