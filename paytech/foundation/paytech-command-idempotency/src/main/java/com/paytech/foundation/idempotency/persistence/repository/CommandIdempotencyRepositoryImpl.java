package com.paytech.foundation.idempotency.persistence.repository;

import java.time.Instant;
import java.util.Optional;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.idempotency.api.CommandIdempotencyService.CommandFailure;
import com.paytech.foundation.idempotency.api.CommandIdempotencyService.CommandRecord;
import com.paytech.foundation.idempotency.api.CommandIdempotencyService.CommandSuccess;
import com.paytech.foundation.idempotency.api.CommandIdempotencyService.NewCommandClaim;
import com.paytech.foundation.idempotency.api.CommandProcessStatus;
import com.paytech.foundation.idempotency.persistence.mapper.CommandIdempotencyMapper;
import com.paytech.foundation.idempotency.persistence.mapper.CommandIdempotencyRow;

import lombok.AllArgsConstructor;

/** MyBatis 实现的命令幂等仓储。 */
@AllArgsConstructor
public final class CommandIdempotencyRepositoryImpl implements CommandIdempotencyRepository {

    private final CommandIdempotencyMapper mapper;

    @Override
    public CommandRecord claim(NewCommandClaim claim) {
        CommandIdempotencyRow candidate = new CommandIdempotencyRow(claim.commandIdempotencyId(), claim.callerScope(),
            claim.operationType(), claim.idempotencyKey(), claim.requestHash(), claim.requestHashVersion(),
            claim.requestSnapshotSchemaVersion(), claim.requestSnapshotJson(), claim.operationContextTime(),
            claim.recoveryStage(), CommandProcessStatus.PROCESSING.getCode(), claim.resourceType(), claim.resourceId(),
            claim.resourceNo(), null, null, null, null, null, claim.lockOwner(), claim.lockUntil(), 1L,
            claim.createTime(), claim.createTime());
        int affectedRows = mapper.insertOrKeepWinner(candidate);
        if (affectedRows < 0 || affectedRows > 1) {
            throw new ErrorException("命令幂等 Claim 插入受影响行数异常: " + affectedRows);
        }
        CommandIdempotencyRow winner =
            mapper.selectByIdentityForUpdate(claim.callerScope(), claim.operationType(), claim.idempotencyKey());
        if (winner == null) {
            throw new ErrorException("命令幂等 Claim 未能锁定唯一 winner");
        }
        return toRecord(winner);
    }

    @Override
    public Optional<CommandRecord> find(String callerScope, String operationType, String idempotencyKey) {
        return Optional.ofNullable(mapper.selectByIdentity(callerScope, operationType, idempotencyKey))
            .map(this::toRecord);
    }

    @Override
    public boolean acquireOrRenewLease(long commandIdempotencyId, long expectedVersion, String expectedStage,
        Instant now, String newLockOwner, Instant newLockUntil) {
        int affectedRows = mapper.acquireOrRenewLease(commandIdempotencyId, expectedVersion, expectedStage, now,
            newLockOwner, newLockUntil);
        if (affectedRows < 0 || affectedRows > 1) {
            throw new ErrorException("命令 Lease CAS 受影响行数异常: " + affectedRows);
        }
        return affectedRows == 1;
    }

    @Override
    public void advanceStage(long commandIdempotencyId, String operationType, long expectedVersion,
        String expectedStage, String nextStage, String lockOwner, Instant newLockUntil, Instant updatedAt) {
        int affectedRows = mapper.advanceStage(commandIdempotencyId, operationType, expectedVersion, expectedStage,
            nextStage, lockOwner, newLockUntil, updatedAt);
        requireCas(affectedRows, "命令恢复阶段");
    }

    @Override
    public void completeSuccess(CommandSuccess completion) {
        complete(completion.commandIdempotencyId(), completion.operationType(), completion.expectedVersion(),
            completion.expectedStage(), completion.completedStage(), CommandProcessStatus.SUCCEEDED,
            completion.lockOwner(), completion.responseCode(), completion.responseHttpStatus(),
            completion.responseSnapshotSchemaVersion(), completion.responseLocation(), completion.responseBodyJson(),
            completion.completedAt());
    }

    @Override
    public void completeFailure(CommandFailure failure) {
        complete(failure.commandIdempotencyId(), failure.operationType(), failure.expectedVersion(),
            failure.expectedStage(), failure.completedStage(), CommandProcessStatus.FAILED, failure.lockOwner(),
            failure.responseCode(), failure.responseHttpStatus(), failure.responseSnapshotSchemaVersion(),
            failure.responseLocation(), failure.responseBodyJson(), failure.completedAt());
    }

    private void complete(long commandIdempotencyId, String operationType, long expectedVersion, String expectedStage,
        String completedStage, CommandProcessStatus status, String lockOwner, String responseCode,
        int responseHttpStatus, Integer responseSnapshotSchemaVersion, String responseLocation, String responseBodyJson,
        Instant completedAt) {
        int affectedRows = mapper.complete(commandIdempotencyId, operationType, expectedVersion, expectedStage,
            completedStage, status.getCode(), lockOwner, responseCode, responseHttpStatus,
            responseSnapshotSchemaVersion, responseLocation, responseBodyJson, completedAt);
        requireCas(affectedRows, "命令稳定回执");
    }

    private CommandRecord toRecord(CommandIdempotencyRow row) {
        try {
            return new CommandRecord(row.commandIdempotencyId(), row.callerScope(), row.operationType(),
                row.idempotencyKey(), row.requestHash(), row.requestHashVersion(), row.requestSnapshotSchemaVersion(),
                row.requestSnapshotJson(), row.operationContextTime(), row.recoveryStage(),
                CommandProcessStatus.fromCode(row.processStatus()), row.resourceType(), row.resourceId(),
                row.resourceNo(), row.responseCode(), row.responseHttpStatus(), row.responseSnapshotSchemaVersion(),
                row.responseLocation(), row.responseBodyJson(), row.lockOwner(), row.lockUntil(), row.version(),
                row.createTime(), row.updateTime());
        } catch (IllegalArgumentException exception) {
            throw new ErrorException("命令幂等记录包含未知处理状态", exception);
        }
    }

    private void requireCas(int affectedRows, String operation) {
        if (affectedRows != 1) {
            throw new ConcurrentRequestException(operation + "已被其他请求推进");
        }
    }
}
