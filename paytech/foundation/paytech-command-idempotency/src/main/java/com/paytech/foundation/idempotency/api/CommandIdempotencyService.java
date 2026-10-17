package com.paytech.foundation.idempotency.api;

import java.time.Instant;
import java.util.Optional;

/**
 * 服务本地同步命令的幂等持久化能力。
 *
 * <p>
 * 该能力只管理 Claim、Lease、CAS、恢复阶段和稳定响应快照，不计算业务请求 Hash，不解释恢复阶段， 也不创建事务。所有写方法必须在调用服务已经开启的本地可写事务中执行。
 */
public interface CommandIdempotencyService {

    /** 插入候选记录并锁定唯一元组，返回真实 winner。 */
    CommandRecord claim(NewCommandClaim claim);

    /** 不加行锁读取同一调用方命名空间内的命令记录。 */
    Optional<CommandRecord> find(String callerScope, String operationType, String idempotencyKey);

    /** 当前 Owner 可续租，其他 Owner 只能接管已经到期的 PROCESSING Lease。 */
    boolean acquireOrRenewLease(long commandIdempotencyId, long expectedVersion, String expectedStage, Instant now,
        String newLockOwner, Instant newLockUntil);

    /** 持有有效 Lease 时通过 CAS 推进业务定义的恢复阶段。 */
    void advanceStage(long commandIdempotencyId, String operationType, long expectedVersion, String expectedStage,
        String nextStage, String lockOwner, Instant newLockUntil, Instant updatedAt);

    /** 冻结成功响应并进入调用方指定的完成阶段。 */
    void completeSuccess(CommandSuccess completion);

    /** 冻结稳定业务失败并进入调用方指定的完成阶段。 */
    void completeFailure(CommandFailure failure);

    /** 首次 Claim 使用的业务冻结值；字段语义和内容由调用服务拥有。 */
    record NewCommandClaim(long commandIdempotencyId, String callerScope, String operationType, String idempotencyKey,
        String requestHash, int requestHashVersion, Integer requestSnapshotSchemaVersion, String requestSnapshotJson,
        Instant operationContextTime, String recoveryStage, String resourceType, Long resourceId, String resourceNo,
        String lockOwner, Instant lockUntil, Instant createTime) {
    }

    /** 可供调用服务重放结果或继续恢复的完整命令记录。 */
    record CommandRecord(long commandIdempotencyId, String callerScope, String operationType, String idempotencyKey,
        String requestHash, int requestHashVersion, Integer requestSnapshotSchemaVersion, String requestSnapshotJson,
        Instant operationContextTime, String recoveryStage, CommandProcessStatus processStatus, String resourceType,
        Long resourceId, String resourceNo, String responseCode, Integer responseHttpStatus,
        Integer responseSnapshotSchemaVersion, String responseLocation, String responseBodyJson, String lockOwner,
        Instant lockUntil, long version, Instant createTime, Instant updateTime) {

        /** 判断记录是否处于给定任一技术状态。 */
        public boolean statusIn(CommandProcessStatus... statuses) {
            if (statuses == null) {
                return false;
            }
            for (CommandProcessStatus status : statuses) {
                if (status == processStatus) {
                    return true;
                }
            }
            return false;
        }
    }

    /** 成功命令的响应快照与当前 CAS 坐标。 */
    record CommandSuccess(long commandIdempotencyId, String operationType, long expectedVersion, String expectedStage,
        String completedStage, String lockOwner, String responseCode, int responseHttpStatus,
        Integer responseSnapshotSchemaVersion, String responseLocation, String responseBodyJson, Instant completedAt) {
    }

    /** 稳定业务失败的响应快照与当前 CAS 坐标。 */
    record CommandFailure(long commandIdempotencyId, String operationType, long expectedVersion, String expectedStage,
        String completedStage, String lockOwner, String responseCode, int responseHttpStatus,
        Integer responseSnapshotSchemaVersion, String responseLocation, String responseBodyJson, Instant completedAt) {
    }
}
