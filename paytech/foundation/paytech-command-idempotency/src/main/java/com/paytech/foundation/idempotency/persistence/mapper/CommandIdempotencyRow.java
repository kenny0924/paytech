package com.paytech.foundation.idempotency.persistence.mapper;

import java.time.Instant;

/** Foundation Mapper 使用的服务本地命令幂等表记录。 */
public record CommandIdempotencyRow(long commandIdempotencyId, String callerScope, String operationType,
    String idempotencyKey, String requestHash, int requestHashVersion, Integer requestSnapshotSchemaVersion,
    String requestSnapshotJson, Instant operationContextTime, String recoveryStage, String processStatus,
    String resourceType, Long resourceId, String resourceNo, String responseCode, Integer responseHttpStatus,
    Integer responseSnapshotSchemaVersion, String responseLocation, String responseBodyJson, String lockOwner,
    Instant lockUntil, long version, Instant createTime, Instant updateTime) {
}
