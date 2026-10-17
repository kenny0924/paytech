package com.paytech.foundation.compensation.persistence.mapper;

import java.util.List;
import java.util.Set;

import org.apache.ibatis.annotations.Param;

import com.paytech.foundation.compensation.model.CompensationEventRecord;

/**
 * 服务自有补偿事件表的 MyBatis 映射接口。
 *
 * @author Codex
 */
public interface CompensationEventMapper {

    int insert(CompensationEventRecord record);

    CompensationEventRecord selectByIdentity(@Param("eventKey") String eventKey, @Param("eventType") String eventType,
        @Param("subEventType") String subEventType);

    CompensationEventRecord selectByIdentityForUpdate(@Param("eventKey") String eventKey,
        @Param("eventType") String eventType, @Param("subEventType") String subEventType);

    CompensationEventRecord selectByIdForRecovery(@Param("eventId") long eventId);

    int claimReady(@Param("eventId") long eventId, @Param("lockOwner") String lockOwner,
        @Param("leaseMicros") long leaseMicros);

    int claimExpiredLease(@Param("eventId") long eventId, @Param("lockOwner") String lockOwner,
        @Param("leaseMicros") long leaseMicros);

    CompensationEventRecord selectClaimed(@Param("eventId") long eventId, @Param("lockOwner") String lockOwner);

    List<Long> selectCandidateIds(@Param("batchSize") int batchSize, @Param("shardIndex") int shardIndex,
        @Param("shardCount") int shardCount, @Param("eventTypes") Set<String> eventTypes);

    CompensationBacklogRecord selectBacklog();

    int deleteClaimed(@Param("eventId") long eventId, @Param("lockOwner") String lockOwner,
        @Param("lockVersion") long lockVersion);

    int markRetry(@Param("eventId") long eventId, @Param("lockOwner") String lockOwner,
        @Param("lockVersion") long lockVersion, @Param("delayMicros") long delayMicros,
        @Param("errorCode") String errorCode, @Param("errorSummary") String errorSummary);

    int markFailed(@Param("eventId") long eventId, @Param("lockOwner") String lockOwner,
        @Param("lockVersion") long lockVersion, @Param("errorCode") String errorCode,
        @Param("errorSummary") String errorSummary);

    int reopenFailed(@Param("eventId") long eventId, @Param("expectedLockVersion") long expectedLockVersion);
}
