package com.paytech.platform.support.auditsecurity.domainservice.repository;

import java.time.Instant;
import java.time.LocalDate;

import com.paytech.platform.support.auditsecurity.core.dal.dataobject.OperationsAuditChain;

/** Operations Audit 的本库事实访问接口。 */
public interface OperationsAuditWriteRepository {
    void ensureChain(long id, String key, LocalDate date, Instant now);

    OperationsAuditChain lockChain(LocalDate date);

    void insertEvent(long id, long chainId, int sequence, String previous, String attributeHash, String eventHash,
        String actorType, String actorId, Long tenantId, String action, String resourceType, String resourceId, String result,
        String requestId, Instant now, Instant receivedAt);

    void insertAttribute(long id, long eventId, int number, String key, String value, String hash, Instant now);

    int advanceChain(long id, int sequence, String hash, Instant now);

    void insertSecurity(long id, String action, String actorId, Long tenantId, String eventId, String hash,
        Instant now, Instant receivedAt);

}
