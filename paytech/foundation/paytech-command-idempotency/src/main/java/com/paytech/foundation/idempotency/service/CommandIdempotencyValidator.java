package com.paytech.foundation.idempotency.service;

import java.time.Instant;
import java.util.Objects;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.idempotency.api.CommandIdempotencyService.CommandFailure;
import com.paytech.foundation.idempotency.api.CommandIdempotencyService.CommandSuccess;
import com.paytech.foundation.idempotency.api.CommandIdempotencyService.NewCommandClaim;

/** 命令幂等公共机制的参数与事务边界校验。 */
final class CommandIdempotencyValidator {

    void validateClaim(NewCommandClaim claim) {
        NewCommandClaim required = Objects.requireNonNull(claim, "claim");
        requirePositive(required.commandIdempotencyId(), "commandIdempotencyId");
        validateIdentity(required.callerScope(), required.operationType(), required.idempotencyKey());
        requireText(required.requestHash(), "requestHash");
        requirePositive(required.requestHashVersion(), "requestHashVersion");
        requireText(required.recoveryStage(), "recoveryStage");
        requireText(required.lockOwner(), "lockOwner");
        Instant contextTime = Objects.requireNonNull(required.operationContextTime(), "operationContextTime");
        Instant lockUntil = Objects.requireNonNull(required.lockUntil(), "lockUntil");
        Objects.requireNonNull(required.createTime(), "createTime");
        if (!lockUntil.isAfter(contextTime)) {
            throw new IllegalArgumentException("lockUntil 必须晚于 operationContextTime");
        }
    }

    void validateIdentity(String callerScope, String operationType, String idempotencyKey) {
        requireText(callerScope, "callerScope");
        requireText(operationType, "operationType");
        requireText(idempotencyKey, "idempotencyKey");
    }

    void validateLease(long commandIdempotencyId, long expectedVersion, String expectedStage, Instant now,
        String lockOwner, Instant lockUntil) {
        requirePositive(commandIdempotencyId, "commandIdempotencyId");
        requirePositive(expectedVersion, "expectedVersion");
        requireText(expectedStage, "expectedStage");
        requireText(lockOwner, "newLockOwner");
        Instant currentTime = Objects.requireNonNull(now, "now");
        if (!Objects.requireNonNull(lockUntil, "newLockUntil").isAfter(currentTime)) {
            throw new IllegalArgumentException("newLockUntil 必须晚于 now");
        }
    }

    void validateTransition(long commandIdempotencyId, String operationType, long expectedVersion, String expectedStage,
        String nextStage, String lockOwner, Instant lockUntil, Instant updatedAt) {
        requirePositive(commandIdempotencyId, "commandIdempotencyId");
        requireText(operationType, "operationType");
        requirePositive(expectedVersion, "expectedVersion");
        String currentStage = requireText(expectedStage, "expectedStage");
        if (currentStage.equals(requireText(nextStage, "nextStage"))) {
            throw new IllegalArgumentException("nextStage 必须不同于 expectedStage");
        }
        requireText(lockOwner, "lockOwner");
        Instant updateTime = Objects.requireNonNull(updatedAt, "updatedAt");
        if (!Objects.requireNonNull(lockUntil, "newLockUntil").isAfter(updateTime)) {
            throw new IllegalArgumentException("newLockUntil 必须晚于 updatedAt");
        }
    }

    void validateSuccess(CommandSuccess completion) {
        CommandSuccess required = Objects.requireNonNull(completion, "completion");
        validateCompletion(required.commandIdempotencyId(), required.operationType(), required.expectedVersion(),
            required.expectedStage(), required.completedStage(), required.lockOwner(), required.responseCode(),
            required.responseHttpStatus(), required.completedAt());
        if (required.responseHttpStatus() < 200 || required.responseHttpStatus() >= 400) {
            throw new IllegalArgumentException("成功命令必须冻结 2xx 或 3xx HTTP 状态");
        }
    }

    void validateFailure(CommandFailure failure) {
        CommandFailure required = Objects.requireNonNull(failure, "failure");
        validateCompletion(required.commandIdempotencyId(), required.operationType(), required.expectedVersion(),
            required.expectedStage(), required.completedStage(), required.lockOwner(), required.responseCode(),
            required.responseHttpStatus(), required.completedAt());
        if (required.responseHttpStatus() < 400 || required.responseHttpStatus() >= 500) {
            throw new IllegalArgumentException("稳定业务失败必须冻结 4xx HTTP 状态");
        }
    }

    void requireWritableTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
            || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new ErrorException("命令幂等写操作必须加入调用服务的本地可写事务");
        }
    }

    private void validateCompletion(long commandIdempotencyId, String operationType, long expectedVersion,
        String expectedStage, String completedStage, String lockOwner, String responseCode, int responseHttpStatus,
        Instant completedAt) {
        requirePositive(commandIdempotencyId, "commandIdempotencyId");
        requireText(operationType, "operationType");
        requirePositive(expectedVersion, "expectedVersion");
        requireText(expectedStage, "expectedStage");
        requireText(completedStage, "completedStage");
        requireText(lockOwner, "lockOwner");
        requireText(responseCode, "responseCode");
        if (responseHttpStatus < 100 || responseHttpStatus > 599) {
            throw new IllegalArgumentException("responseHttpStatus 超出 HTTP 状态范围");
        }
        Objects.requireNonNull(completedAt, "completedAt");
    }

    private void requirePositive(long value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " 必须为正数");
        }
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value;
    }
}
