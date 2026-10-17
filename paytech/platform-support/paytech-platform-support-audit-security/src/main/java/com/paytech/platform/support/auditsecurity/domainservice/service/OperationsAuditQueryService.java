package com.paytech.platform.support.auditsecurity.domainservice.service;

import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditQueryModels.AuditDetails;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditQueryModels.AuditEventPage;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditQueryModels.AuditSearch;

/** Operations 审计范围查询和已批准的安全属性投影。 */
public interface OperationsAuditQueryService {
    AuditEventPage auditEvents(int page, int size, AuditSearch request);

    AuditDetails auditEvent(long eventId);
}
