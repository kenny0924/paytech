package com.paytech.platform.support.accesscontrol.service.processor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domain.operations.OperationsNavigationResourceView;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsDirectoryRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsSessionService;
import com.paytech.platform.support.accesscontrol.service.processor.request.QueryOperationsNavigationRequest;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;

import lombok.AllArgsConstructor;

/** PLT-I21 按当前普通会话及有效权限查询 Operations 导航。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class QueryOperationsNavigationProcessor
    extends AbstractProcessor<QueryOperationsNavigationRequest, List<OperationsNavigationResourceView>> {
    private final OperationsAuditService audit;
    private final OperationsSessionService sessions;
    private final PlatformTransactionManager transactionManager;
    private final OperationsDirectoryRepository directory;

    /** 保留身份和有效角色权限的同一只读快照，避免授权变化时混用前后事实。 */
    @Override
    protected List<OperationsNavigationResourceView> processRequest(QueryOperationsNavigationRequest request) {
        TransactionTemplate query = new TransactionTemplate(transactionManager);
        query.setReadOnly(true);
        query.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        query.setTimeout(15);
        return query.execute(status -> queryNavigation(request));
    }

    /** 按权限读取资源，再逐层剔除失去可见父节点的后代，保持 SQL 排序。 */
    List<OperationsNavigationResourceView> queryNavigation(QueryOperationsNavigationRequest request) {
        audit.observeTransaction();
        var current = sessions.requireCurrent(request.getAccountId(), request.getSessionId());
        sessions.requireOrdinary(current);
        List<OperationsNavigationResourceView> rows = new ArrayList<>(directory.resources(current.member().id()));
        boolean changed;
        do {
            Set<Long> ids = rows.stream().map(OperationsNavigationResourceView::getId).collect(Collectors.toSet());
            changed = rows.removeIf(row -> row.getParentId() != null && !ids.contains(row.getParentId()));
        } while (changed);
        return List.copyOf(rows);
    }
}
