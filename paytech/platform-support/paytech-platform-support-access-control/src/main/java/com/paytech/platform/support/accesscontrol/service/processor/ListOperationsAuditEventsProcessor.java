package com.paytech.platform.support.accesscontrol.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.accesscontrol.service.processor.request.ListOperationsAuditEventsRequest;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditQueryModels.AuditEvent;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditQueryModels.AuditSearch;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditQueryService;

import lombok.AllArgsConstructor;

/** ListOperationsAuditEvents 用例：授权、事实校验及本地事务编排。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ListOperationsAuditEventsProcessor
    extends AbstractProcessor<ListOperationsAuditEventsRequest, PagingResponseBody<AuditEvent>> {
    private final OperationsAuditQueryService audits;
    private final OperationsManagementSupport support;
    private final OperationsManagementTransactions transactions;
    private final OperationsManagementPolicyService policy;

    /** 授权及响应事实共享原有只读一致性快照。 */
    @Override
    protected PagingResponseBody<AuditEvent> processRequest(ListOperationsAuditEventsRequest request) {
        return transactions.query(() -> {
            policy.requirePermission(request.getActor(), request.getSession(), "OPERATIONS_AUDIT_READ");
            new PagingRequestBody(request.getPage(), request.getSize()).offset();
            var search = new AuditSearch(request.getOccurredFrom(), request.getOccurredTo(), request.getActorId(),
                request.getActionCode(), request.getResult(), request.getRequestId(), request.getResourceType(),
                request.getResourceId());
            var result = audits.auditEvents(request.getPage(), request.getSize(), search);
            return new PagingResponseBody<>(result.items(), request.getPage(), request.getSize(), result.total());
        });
    }
}
