package com.paytech.platform.support.auditsecurity.domainservice.service.impl;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.platform.support.auditsecurity.core.dal.dataobject.OperationsAuditRecord;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditQuery;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditQueryModels.AuditAttribute;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditQueryModels.AuditDetails;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditQueryModels.AuditEvent;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditQueryModels.AuditEventPage;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditQueryModels.AuditSearch;
import com.paytech.platform.support.auditsecurity.domainservice.repository.OperationsAuditRepository;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditQueryService;

import lombok.AllArgsConstructor;

/** Operations 审计范围查询和已批准的安全属性投影。 */
@Service
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class OperationsAuditQueryServiceImpl implements OperationsAuditQueryService {
    private final OperationsAuditRepository audits;

    @Override
    public AuditEventPage auditEvents(int page, int size, AuditSearch request) {
        int offset = Math.multiplyExact(page - 1, size);
        var query = new OperationsAuditQuery(request.occurredFrom(), request.occurredTo(), request.actorId(),
            request.actionCode(), request.result(), request.requestId(), request.resourceType(), request.resourceId());
        return new AuditEventPage(audits.events(query, offset, size).stream().map(this::auditEventResponse).toList(),
            audits.eventCount(query));
    }

    @Override
    public AuditDetails auditEvent(long eventId) {
        OperationsAuditRecord event = audits.event(eventId);
        if (event == null) {
            throw new BizCheckFailException("审计事件不存在或不在 Operations 审计范围内");
        }
        var attributes = audits.attributes(event.id()).stream()
            .map(row -> new AuditAttribute(row.number(), row.key(), row.value())).toList();
        return new AuditDetails(auditEventResponse(event), attributes);
    }

    private AuditEvent auditEventResponse(OperationsAuditRecord row) {
        return new AuditEvent(Long.toString(row.id()), row.actorId(), row.actionCode(), row.resourceType(),
            row.resourceId(), row.result(), row.requestId(), row.occurredTime(), row.reason());
    }
}
