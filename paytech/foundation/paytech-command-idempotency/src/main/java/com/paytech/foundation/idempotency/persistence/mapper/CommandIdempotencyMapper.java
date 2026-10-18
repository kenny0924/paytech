package com.paytech.foundation.idempotency.persistence.mapper;

import java.time.Instant;

import org.apache.ibatis.annotations.Param;

/** 服务本地 {@code t_command_idempotency} 表的通用 MyBatis Mapper。 */
public interface CommandIdempotencyMapper {

    int insertOrKeepWinner(CommandIdempotencyRow record);

    CommandIdempotencyRow selectByIdentityForUpdate(@Param("callerScope") String callerScope,
        @Param("operationType") String operationType, @Param("idempotencyKey") String idempotencyKey);

    CommandIdempotencyRow selectByIdentity(@Param("callerScope") String callerScope,
        @Param("operationType") String operationType, @Param("idempotencyKey") String idempotencyKey);

    int acquireOrRenewLease(@Param("commandIdempotencyId") long commandIdempotencyId,
        @Param("expectedVersion") long expectedVersion, @Param("expectedStage") String expectedStage,
        @Param("now") Instant now, @Param("newLockOwner") String newLockOwner,
        @Param("newLockUntil") Instant newLockUntil);

    int advanceStage(@Param("commandIdempotencyId") long commandIdempotencyId,
        @Param("operationType") String operationType, @Param("expectedVersion") long expectedVersion,
        @Param("expectedStage") String expectedStage, @Param("nextStage") String nextStage,
        @Param("lockOwner") String lockOwner, @Param("newLockUntil") Instant newLockUntil,
        @Param("updatedAt") Instant updatedAt);

    int complete(@Param("commandIdempotencyId") long commandIdempotencyId, @Param("operationType") String operationType,
        @Param("expectedVersion") long expectedVersion, @Param("expectedStage") String expectedStage,
        @Param("completedStage") String completedStage, @Param("completedStatus") String completedStatus,
        @Param("lockOwner") String lockOwner, @Param("responseCode") String responseCode,
        @Param("responseHttpStatus") int responseHttpStatus,
        @Param("responseSnapshotSchemaVersion") Integer responseSnapshotSchemaVersion,
        @Param("responseLocation") String responseLocation, @Param("responseBodyJson") String responseBodyJson,
        @Param("completedAt") Instant completedAt);
}
