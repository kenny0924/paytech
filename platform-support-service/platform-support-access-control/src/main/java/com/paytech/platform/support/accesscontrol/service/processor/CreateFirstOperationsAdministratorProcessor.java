package com.paytech.platform.support.accesscontrol.service.processor;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.core.configuration.OperationsAuthenticationProperties;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.*;
import com.paytech.platform.support.accesscontrol.domain.authentication.FirstOperationsAdministratorContext;
import com.paytech.platform.support.accesscontrol.domain.authentication.OperationsAuthenticationModels.AccountCreatedResult;
import com.paytech.platform.support.accesscontrol.domain.authentication.OperationsAuthenticationModels.PreparedHumanCredentials;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsCredentialStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsMemberStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantOwnerStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantSecurityStatus;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsAccountRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsRoleRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsTenantRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsAuthenticationService;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsSessionService;
import com.paytech.platform.support.accesscontrol.service.processor.request.FirstOperationsAdministratorRequest;
import com.paytech.platform.support.api.enums.OperationsAccountStatus;
import com.paytech.platform.support.api.enums.OperationsAccountType;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Target;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.Outcome;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;

import lombok.AllArgsConstructor;

/** 创建首个管理员并永久关闭 Bootstrap。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class CreateFirstOperationsAdministratorProcessor
    extends AbstractLockProcessor<FirstOperationsAdministratorRequest, AccountCreatedResult> {
    private final IdGenerator ids;
    private final OperationsAuditService audit;
    private final Clock clock;
    private final OperationsRoleRepository roles;
    private final OperationsHumanCredentialsPreparation humans;
    private final OperationsTenantRepository tenants;
    private final OperationsAccountRepository accounts;
    private final OperationsSessionService sessions;
    private final OperationsAuthenticationProperties properties;
    private final OperationsAuthenticationService authentication;
    private final PlatformTransactionManager transactionManager;

    /** 同资源入口共用实际 Key；模板统一添加 lock 前缀。 */
    @Override
    protected LockCommand lockCommand(FirstOperationsAdministratorRequest request) {
        return LockCommand.tryLock("operations:account:" + request.getAccountId(),
            Math.toIntExact(properties.getLockWaitTime().toMillis()),
            Math.toIntExact(properties.getLockLeaseTime().toMillis()));
    }

    /** 凭据准备在事务外；新管理员和 Bootstrap 关闭必须原子提交。 */
    @Override
    protected AccountCreatedResult processWithinLock(FirstOperationsAdministratorRequest request) {
        var prepared = humans.prepareHuman(request.getEmail(), request.getInitialPassword());
        try {
            return transaction().execute(status -> {
                audit.observeTransaction();
                var context = buildContext(request, prepared);
                authentication.createFirstAdministrator(context);
                audit.record(() -> Outcome.succeeded(Long.toString(context.getBootstrap().id()), null,
                    new Target("USER_ACCOUNT", Long.toString(context.getAccount().id())), "FIRST_ADMINISTRATOR_CREATED",
                    context.getNow()));
                return new AccountCreatedResult(context.getAccount().id(), context.getMember().id(),
                    context.getAccount().version());
            });
        } catch (DuplicateKeyException failure) {
            // 只在完整回滚后识别本能力拥有的邮箱唯一冲突，不吞其他数据库错误。
            if (String.valueOf(failure.getMostSpecificCause().getMessage()).contains("uk_user_account_email")
                && accounts.loginAccount(prepared.vaultToken(), false) != null) {
                throw new BizCheckFailException("无法创建账号：该登录邮箱已被使用", failure);
            }
            throw failure;
        }
    }

    /** 持有 Bootstrap 数据库锁后，检查唯一交接资格并完整构造新事实。 */
    FirstOperationsAdministratorContext buildContext(FirstOperationsAdministratorRequest request,
        PreparedHumanCredentials prepared) {
        OperationsAccount bootstrap = accounts.lockBootstrapAccount(request.getAccountId());
        if (bootstrap == null) {
            throw new BizCheckFailException("无法创建首次管理员：初始化账号不可用");
        }
        var current =
            sessions.requireCurrent(Long.toString(request.getAccountId()), Long.toString(request.getSessionId()));
        if (current.account().type() != OperationsAccountType.BOOTSTRAP_ADMIN || current.password().forceChange()) {
            throw new BizCheckFailException("只有已修改初始密码的初始化账号才能创建首次管理员");
        }
        Long tenant = tenants.internalTenant();
        if (tenant == null || accounts.humanAccountCount() != 0) {
            throw new BizCheckFailException("无法创建首次管理员：初始化目录不可用或已有人员账号");
        }
        OperationsRole role = roles.roles().stream()
            .filter(item -> item.systemRole() && item.platformRole() && "PLATFORM_ADMIN".equals(item.code()))
            .findFirst().orElseThrow(() -> new ErrorException("PLATFORM_ADMIN 手工初始化缺失"));
        if (accounts.loginAccount(prepared.vaultToken(), false) != null) {
            throw new BizCheckFailException("无法创建账号：该登录邮箱已被使用");
        }
        return newContext(bootstrap, current.password(), current.session(), prepared, tenant, role);
    }

    /** 新对象在插入前准备标识与初始状态，不重新查询构造响应。 */
    FirstOperationsAdministratorContext newContext(OperationsAccount bootstrap, OperationsPassword bootstrapPassword,
        OperationsSession bootstrapSession, PreparedHumanCredentials prepared, long tenant, OperationsRole role) {
        var account = new OperationsAccount(ids.nextId(), OperationsAccountType.HUMAN, prepared.vaultToken(),
            prepared.maskedEmail(), OperationsAccountStatus.ACTIVE, 0, null, 1, 1, 1);
        var password = new OperationsPassword(ids.nextId(), account.id(), prepared.verifier(), 19, 19456, 2, 1, 1, true,
            OperationsCredentialStatus.ACTIVE);
        var member = new OperationsMember(ids.nextId(), tenant, account.id(), OperationsMemberStatus.ACTIVE,
            OperationsTenantOwnerStatus.ACTIVE, OperationsTenantSecurityStatus.ACTIVE, 1);
        return new FirstOperationsAdministratorContext(bootstrap, bootstrapPassword, bootstrapSession, account,
            password, member, role, ids.nextId(), clock.instant().truncatedTo(ChronoUnit.MICROS));
    }

    /** 固定本地写事务范围；审计只观察提交，不阻断主业务。 */
    TransactionTemplate transaction() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(15);
        return transaction;
    }
}
