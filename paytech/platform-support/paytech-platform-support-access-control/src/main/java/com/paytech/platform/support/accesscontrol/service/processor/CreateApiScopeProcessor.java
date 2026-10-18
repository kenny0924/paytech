package com.paytech.platform.support.accesscontrol.service.processor;

import static com.paytech.platform.support.accesscontrol.domain.apimanagement.ClientApiManagementPermissions.SCOPE_MANAGE;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.core.configuration.ApiScopeManagementProperties;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ApiScope;
import com.paytech.platform.support.accesscontrol.domain.apimanagement.ClientApiManagementModels.Mutation;
import com.paytech.platform.support.accesscontrol.domain.apimanagement.CreateApiScopeContext;
import com.paytech.platform.support.accesscontrol.domainservice.repository.ApiScopeRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.ApiScopeManagementService;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.accesscontrol.service.processor.request.CreateApiScopeRequest;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Identity;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Target;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.ConfigurationChanged;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.ConfigurationField;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.ConfigurationFieldChange;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;

import lombok.AllArgsConstructor;

/** 创建全局 Scope 目录，按编码协调并由唯一索引保护业务身份。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class CreateApiScopeProcessor extends AbstractLockProcessor<CreateApiScopeRequest, Mutation> {

    private final Clock clock;
    private final IdGenerator ids;
    private final ApiScopeRepository repository;
    private final OperationsAuditService audit;
    private final ApiScopeManagementService scopes;
    private final ApiScopeManagementProperties properties;
    private final OperationsManagementPolicyService policy;
    private final PlatformTransactionManager transactionManager;

    /** 创建与修改共用 scopeCode 资源；模板添加 lock: 前缀，锁覆盖本地事务。 */
    @Override
    protected LockCommand lockCommand(CreateApiScopeRequest request) {
        return LockCommand.tryLock("api-scope:" + request.getScopeCode(),
            Math.toIntExact(properties.getLockWaitTime().toMillis()),
            Math.toIntExact(properties.getLockLeaseTime().toMillis()));
    }

    /** 在资源锁内开启本库短事务，提交或回滚后由模板释放资源锁。 */
    @Override
    protected Mutation processWithinLock(CreateApiScopeRequest request) {
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
    Mutation execute(CreateApiScopeRequest request) {
        audit.observeTransaction();
        var actor = policy.authorizeManagement(request.getActor(), request.getSession(), null, List.of(), null, null,
            false, SCOPE_MANAGE);
        if (repository.scope(request.getScopeCode()) != null) {
            throw new BizCheckFailException("Scope 编码已经存在");
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        ApiScope scope = new ApiScope(ids.nextId(), request.getScopeCode(), request.getDisplayName(),
            request.getDescription(), 1, now, now);
        CreateApiScopeContext context = new CreateApiScopeContext(scope);
        scopes.create(context);
        audit.record(() -> auditFact(request, context, actor.member().tenantId()));
        return new Mutation(scope.getApiScopeId(), scope.getScopeCode(), scope.getVersion());
    }

    /** 冻结白名单字段差异；更新前捕获原值，沿用提交后的既有审计链。 */
    ConfigurationChanged auditFact(CreateApiScopeRequest request, CreateApiScopeContext context, long tenantId) {
        ApiScope scope = context.getScope();
        var changes = List.of(new ConfigurationFieldChange(ConfigurationField.CODE, null, scope.getScopeCode()),
            new ConfigurationFieldChange(ConfigurationField.DISPLAY_NAME, null, scope.getDisplayName()),
            new ConfigurationFieldChange(ConfigurationField.DESCRIPTION, null, scope.getDescription()));
        return new ConfigurationChanged(new Identity(request.getActor(), tenantId),
            new Target("API_SCOPE", scope.getScopeCode()), 0, scope.getVersion(), changes, request.getReason(),
            scope.getUpdateTime());
    }
}
