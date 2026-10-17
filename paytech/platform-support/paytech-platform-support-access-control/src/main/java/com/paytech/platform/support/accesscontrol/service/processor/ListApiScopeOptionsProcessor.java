package com.paytech.platform.support.accesscontrol.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ApiScope;
import com.paytech.platform.support.accesscontrol.domain.apimanagement.ClientApiManagementModels.Scope;
import com.paytech.platform.support.accesscontrol.domainservice.repository.ApiScopeRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.accesscontrol.service.processor.request.ListApiScopesRequest;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;

import lombok.AllArgsConstructor;

/** 为路由选择器独立检查读取权限并分页查询。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ListApiScopeOptionsProcessor extends AbstractProcessor<ListApiScopesRequest, PagingResponseBody<Scope>> {

    private final ApiScopeRepository repository;
    private final OperationsAuditService audit;
    private final PlatformTransactionManager transactionManager;
    private final OperationsManagementPolicyService policy;

    /** 保留权限、目录及分页总数的同一只读快照，避免混用不同提交时点。 */
    @Override
    protected PagingResponseBody<Scope> processRequest(ListApiScopesRequest request) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setReadOnly(true);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        transaction.setTimeout(15);
        return transaction.execute(status -> query(request));
    }

    /** 当前普通会话和独立权限通过后，再读取本模块目录。 */
    PagingResponseBody<Scope> query(ListApiScopesRequest request) {
        audit.observeTransaction();
        policy.requirePermission(request.getActor(), request.getSession(), "GATEWAY_CONFIG_READ");
        var paging = request.getPaging();
        var items = repository.scopes(request.getKeyword(), paging.offset(), paging.size()).stream()
            .map(this::toScope).toList();
        long total = repository.count(request.getKeyword());
        return new PagingResponseBody<>(items, paging.page(), paging.size(), total);
    }

    /** 在 Owner 边界校验记录并转换为既有领域合同，不向 Web 返回 DataObject。 */
    Scope toScope(ApiScope scope) {
        scope.validateForManagement();
        return new Scope(scope.getApiScopeId(), scope.getScopeCode(), scope.getDisplayName(), scope.getDescription(),
            scope.getVersion(), scope.getCreateTime(), scope.getUpdateTime());
    }
}
