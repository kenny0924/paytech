package com.paytech.platform.support.accesscontrol.service.processor;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.core.configuration.OperationsAuthenticationProperties;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsAccount;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsMember;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsPassword;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsCredentialStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsMemberStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantOwnerStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantSecurityStatus;
import com.paytech.platform.support.accesscontrol.domain.operations.CreateOperationsAccountContext;
import com.paytech.platform.support.accesscontrol.domain.operations.OperationsAccountView;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsAccountRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsAccountService;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.accesscontrol.service.processor.request.CreateOperationsAccountRequest;
import com.paytech.platform.support.api.enums.OperationsAccountStatus;
import com.paytech.platform.support.api.enums.OperationsAccountType;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Target;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.Outcome;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;

import lombok.AllArgsConstructor;

/** CreateOperationsAccount 用例：授权、事实校验及本地事务编排。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class CreateOperationsAccountProcessor
    extends AbstractLockProcessor<CreateOperationsAccountRequest, OperationsAccountView> {
    private final Clock clock;
    private final IdGenerator ids;
    private final OperationsAuditService audit;
    private final OperationsAccountService accounts;
    private final OperationsAccountRepository repository;
    private final OperationsManagementSupport support;
    private final OperationsManagementTransactions transactions;
    private final OperationsResourceKeyPreparation resourceKeys;
    private final OperationsManagementPolicyService policy;
    private final OperationsAuthenticationProperties properties;
    private final OperationsHumanCredentialsPreparation humans;

    /** 同资源命令共用 Key，模板统一负责锁及公共 Redis 异常。 */
    @Override
    protected LockCommand lockCommand(CreateOperationsAccountRequest request) {
        String resource = resourceKeys.createAccountSegment(request.getPrepared().vaultToken());
        return LockCommand.tryLock("operations:create-account:" + resource,
            Math.toIntExact(properties.getLockWaitTime().toMillis()),
            Math.toIntExact(properties.getLockLeaseTime().toMillis()));
    }

    /** 本地写入在同一事务内完成；外部准备已在事务前结束。 */
    @Override
    protected OperationsAccountView processWithinLock(CreateOperationsAccountRequest request) {
        try {
            return transactions.write(() -> {
                var actor = policy.authorizeManagement(request.getActor(), request.getSession(), null,
                    request.getRoleIds(), null, null, false, "OPERATIONS_ACCOUNT_CREATE");
                var roleIds = policy.validateRoles(request.getRoleIds());
                policy.requireGrantableRoles(actor, roleIds);
                var prepared = request.getPrepared();
                if (repository.loginAccount(prepared.vaultToken(), false) != null) {
                    throw new BizCheckFailException("无法创建账号：该登录邮箱已被使用");
                }
                long tenant = actor.member().tenantId();
                var account = new OperationsAccount(ids.nextId(), OperationsAccountType.HUMAN, prepared.vaultToken(),
                    prepared.maskedEmail(), OperationsAccountStatus.ACTIVE, 0, null, 1, 1, 1);
                var password = new OperationsPassword(ids.nextId(), account.id(), prepared.verifier(), 19, 19456, 2, 1,
                    1, true, OperationsCredentialStatus.ACTIVE);
                var member = new OperationsMember(ids.nextId(), tenant, account.id(), OperationsMemberStatus.ACTIVE,
                    OperationsTenantOwnerStatus.ACTIVE, OperationsTenantSecurityStatus.ACTIVE, 1);
                var context = new CreateOperationsAccountContext(account, member, password, support.roles(roleIds),
                    actor.account().id(), clock.instant().truncatedTo(ChronoUnit.MICROS));
                accounts.createAccount(context);
                audit.record(() -> Outcome.succeeded(request.getActor(), tenant,
                    new Target("USER_ACCOUNT", Long.toString(account.id())), request.getReason(), context.getNow()));
                return support.accountDetails(context);
            });
        } catch (org.springframework.dao.DuplicateKeyException failure) {
            // 仅在整个事务回滚后识别本能力拥有的邮箱唯一键冲突。
            if (String.valueOf(failure.getMostSpecificCause().getMessage()).contains("uk_user_account_email")
                && repository.loginAccount(request.getPrepared().vaultToken(), false) != null) {
                throw new BizCheckFailException("无法创建账号：该登录邮箱已被使用", failure);
            }
            throw failure;
        }
    }

    /** 稳定邮箱 Token 只在事务外准备一次，供真实资源锁和本地保存共同使用。 */
    @Override
    protected OperationsAccountView processRequest(CreateOperationsAccountRequest request) {
        var prepared = humans.prepareHuman(request.getEmail(), request.getInitialPassword());
        var resolved = new CreateOperationsAccountRequest(request.getActor(), request.getSession(), request.getEmail(),
            request.getInitialPassword(), request.getRoleIds(), request.getReason(), prepared);
        return super.processRequest(resolved);
    }
}
