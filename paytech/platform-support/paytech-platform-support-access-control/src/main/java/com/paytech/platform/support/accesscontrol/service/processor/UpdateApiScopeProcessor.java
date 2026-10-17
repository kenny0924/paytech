package com.paytech.platform.support.accesscontrol.service.processor;

import static com.paytech.platform.support.accesscontrol.domain.apimanagement.ClientApiManagementPermissions.SCOPE_MANAGE;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.core.configuration.ApiScopeManagementProperties;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ApiScope;
import com.paytech.platform.support.accesscontrol.domain.apimanagement.ClientApiManagementModels.Mutation;
import com.paytech.platform.support.accesscontrol.domain.apimanagement.UpdateApiScopeContext;
import com.paytech.platform.support.accesscontrol.domainservice.repository.ApiScopeRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.ApiScopeManagementService;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.accesscontrol.service.processor.request.UpdateApiScopeRequest;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Identity;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Target;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.ConfigurationChanged;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.ConfigurationField;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.ConfigurationFieldChange;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;

import lombok.AllArgsConstructor;

/** 修改 Scope 名称和说明，按编码协调并由原版本保护本地变更。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class UpdateApiScopeProcessor extends AbstractLockProcessor<UpdateApiScopeRequest, Mutation> {

    private final Clock clock;
    private final ApiScopeRepository repository;
    private final OperationsAuditService audit;
    private final ApiScopeManagementService scopes;
    private final PlatformTransactionManager transactionManager;
    private final ApiScopeManagementProperties properties;
    private final OperationsManagementPolicyService policy;

    /** 创建与修改共用 scopeCode 资源；模板添加 lock: 前缀，锁覆盖本地事务。 */
    @Override
    protected LockCommand lockCommand(UpdateApiScopeRequest request) {
        return LockCommand.tryLock("api-scope:" + request.getScopeCode(),
            Math.toIntExact(properties.getLockWaitTime().toMillis()),
            Math.toIntExact(properties.getLockLeaseTime().toMillis()));
    }

    /** 在资源锁内开启本库短事务，提交或回滚后由模板释放资源锁。 */
    @Override
    protected Mutation processWithinLock(UpdateApiScopeRequest request) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(15);
        try {
            return transaction.execute(status -> execute(request));
        } catch (ConcurrencyFailureException failure) {
            throw new ConcurrentRequestException("发生并发修改，请刷新后重试", failure);
        }
    }

    /** 当前授权、业务检查和本地保存同属一个事务，不读写请求幂等记录。 */
    Mutation execute(UpdateApiScopeRequest request) {
        audit.observeTransaction();
        var actor = policy.authorizeManagement(request.getActor(), request.getSession(), null, List.of(), null, null,
            false, SCOPE_MANAGE);
        ApiScope scope = requireScope(request);
        UpdateApiScopeContext context = new UpdateApiScopeContext(scope, request.getDisplayName(),
            request.getDescription(), clock.instant().truncatedTo(ChronoUnit.MICROS));
        ConfigurationChanged fact = auditFact(request, context, actor.member().tenantId());
        scopes.update(context);
        audit.record(() -> fact);
        return new Mutation(scope.getApiScopeId(), scope.getScopeCode(), scope.getVersion());
    }

    /** 锁定目录原对象并复核期望版本，拒绝溢出；编码和创建时间保持不变。 */
    ApiScope requireScope(UpdateApiScopeRequest request) {
        ApiScope scope = repository.lock(request.getScopeCode());
        if (scope == null) {
            throw new FailException(CommonErrorCode.NOT_FOUND);
        }
        scope.validateForManagement();
        if (scope.getVersion() != request.getExpectedVersion() || scope.getVersion() == Integer.MAX_VALUE) {
            throw new ConcurrentRequestException("Scope 已被其他操作修改，请刷新后重试");
        }
        return scope;
    }

    /** 冻结白名单字段差异；更新前捕获原值，沿用提交后的既有审计链。 */
    ConfigurationChanged auditFact(UpdateApiScopeRequest request, UpdateApiScopeContext context, long tenantId) {
        ApiScope scope = context.getScope();
        var changes =
            List.of(new ConfigurationFieldChange(ConfigurationField.CODE, scope.getScopeCode(), scope.getScopeCode()),
                new ConfigurationFieldChange(ConfigurationField.DISPLAY_NAME, scope.getDisplayName(),
                    context.getDisplayName()),
                new ConfigurationFieldChange(ConfigurationField.DESCRIPTION, scope.getDescription(),
                    context.getDescription()));
        return new ConfigurationChanged(new Identity(request.getActor(), tenantId),
            new Target("API_SCOPE", scope.getScopeCode()), scope.getVersion(), scope.getVersion() + 1, changes,
            request.getReason(), context.getUpdateTime());
    }
}
