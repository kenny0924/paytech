package com.paytech.platform.support.auditsecurity.core.dal.mapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.auditsecurity.core.dal.dataobject.OperationsAuditAttribute;
import com.paytech.platform.support.auditsecurity.core.dal.dataobject.OperationsAuditChain;
import com.paytech.platform.support.auditsecurity.core.dal.dataobject.OperationsAuditRecord;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditQuery;

/** UTC 日期链与不可变事件的单库 SQL；不接收凭据或认证 Payload。 */
public interface OperationsAuditMapper {

    void ensureChain(@Param("id") long id, @Param("key") String key, @Param("date") LocalDate date,
        @Param("now") Instant now);

    OperationsAuditChain lockChain(LocalDate date);

    void insertEvent(@Param("id") long id, @Param("chainId") long chainId, @Param("sequence") int sequence,
        @Param("previous") String previous, @Param("attributeHash") String attributeHash,
        @Param("eventHash") String eventHash, @Param("actorType") String actorType, @Param("actorId") String actorId, @Param("tenantId") Long tenantId,
        @Param("action") String action, @Param("resourceType") String resourceType,
        @Param("resourceId") String resourceId, @Param("result") String result, @Param("requestId") String requestId,
        @Param("now") Instant now, @Param("receivedAt") Instant receivedAt);

    void insertAttribute(@Param("id") long id, @Param("eventId") long eventId, @Param("number") int number,
        @Param("key") String key, @Param("value") String value, @Param("hash") String hash, @Param("now") Instant now);

    int advanceChain(@Param("id") long id, @Param("sequence") int sequence, @Param("hash") String hash,
        @Param("now") Instant now);

    void insertSecurity(@Param("id") long id, @Param("action") String action, @Param("actorId") String actorId,
        @Param("tenantId") Long tenantId, @Param("eventId") String eventId, @Param("hash") String hash,
        @Param("now") Instant now, @Param("receivedAt") Instant receivedAt);

    List<OperationsAuditRecord> events(@Param("query") OperationsAuditQuery query,
        @Param("offset") int offset, @Param("size") int size);

    long eventCount(@Param("query") OperationsAuditQuery query);

    OperationsAuditRecord event(long id);

    List<OperationsAuditAttribute> attributes(long eventId);
}
