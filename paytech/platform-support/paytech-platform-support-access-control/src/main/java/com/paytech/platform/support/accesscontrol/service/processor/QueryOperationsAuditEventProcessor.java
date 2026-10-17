package com.paytech.platform.support.accesscontrol.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.accesscontrol.service.processor.request.QueryOperationsAuditEventRequest;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditQueryModels.AuditDetails;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditQueryService;

import lombok.AllArgsConstructor;

/** QueryOperationsAuditEvent 用例：授权、事实校验及本地事务编排。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class QueryOperationsAuditEventProcessor
    extends AbstractProcessor<QueryOperationsAuditEventRequest, AuditDetails> {
    private final OperationsAuditQueryService audits;
    private final OperationsManagementSupport support;
    private final OperationsManagementTransactions transactions;
    private final OperationsManagementPolicyService policy;

    /** 授权及响应事实共享原有只读一致性快照。 */
    @Override
    protected AuditDetails processRequest(QueryOperationsAuditEventRequest request) {
        return transactions.query(() -> {
            policy.requirePermission(request.getActor(), request.getSession(), "OPERATIONS_AUDIT_READ");
            return audits.auditEvent(request.getAuditEventId());
        });
    }
}
