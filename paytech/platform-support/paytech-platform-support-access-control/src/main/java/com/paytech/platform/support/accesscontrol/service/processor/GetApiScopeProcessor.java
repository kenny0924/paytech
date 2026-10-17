package com.paytech.platform.support.accesscontrol.service.processor;

import static com.paytech.platform.support.accesscontrol.domain.apimanagement.ClientApiManagementPermissions.SCOPE_READ;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ApiScope;
import com.paytech.platform.support.accesscontrol.domain.apimanagement.ClientApiManagementModels.Scope;
import com.paytech.platform.support.accesscontrol.domainservice.repository.ApiScopeRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.accesscontrol.service.processor.request.GetApiScopeRequest;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;

import lombok.AllArgsConstructor;

/** 按编码读取 Scope 定义，引用聚合继续由 BFF 负责。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class GetApiScopeProcessor extends AbstractProcessor<GetApiScopeRequest, Scope> {

    private final ApiScopeRepository repository;
    private final OperationsAuditService audit;
    private final PlatformTransactionManager transactionManager;
    private final OperationsManagementPolicyService policy;

    /** 保留权限、目录及分页总数的同一只读快照，避免混用不同提交时点。 */
    @Override
    protected Scope processRequest(GetApiScopeRequest request) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setReadOnly(true);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        transaction.setTimeout(15);
        return transaction.execute(status -> query(request));
    }

    /** 当前普通会话和独立权限通过后，再读取本模块目录。 */
    Scope query(GetApiScopeRequest request) {
        audit.observeTransaction();
        policy.requirePermission(request.getActor(), request.getSession(), SCOPE_READ);
        ApiScope scope = repository.scope(request.getScopeCode());
        if (scope == null) {
            throw new FailException(CommonErrorCode.NOT_FOUND);
        }
        return toScope(scope);
    }

    /** 在 Owner 边界校验记录并转换为既有领域合同，不向 Web 返回 DataObject。 */
    Scope toScope(ApiScope scope) {
        scope.validateForManagement();
        return new Scope(scope.getApiScopeId(), scope.getScopeCode(), scope.getDisplayName(), scope.getDescription(),
            scope.getVersion(), scope.getCreateTime(), scope.getUpdateTime());
    }
}
