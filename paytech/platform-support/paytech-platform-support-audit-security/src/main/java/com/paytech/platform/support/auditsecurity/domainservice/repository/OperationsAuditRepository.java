package com.paytech.platform.support.auditsecurity.domainservice.repository;

import java.util.List;

import com.paytech.platform.support.auditsecurity.core.dal.dataobject.OperationsAuditAttribute;
import com.paytech.platform.support.auditsecurity.core.dal.dataobject.OperationsAuditRecord;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditQuery;

/** Operations Audit 的本库事实访问接口。 */
public interface OperationsAuditRepository {
    List<OperationsAuditRecord> events(OperationsAuditQuery query, int offset, int size);

    long eventCount(OperationsAuditQuery query);

    OperationsAuditRecord event(long id);

    List<OperationsAuditAttribute> attributes(long eventId);
}
