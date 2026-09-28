package com.paytech.foundation.idempotency.persistence.repository;

import java.time.Instant;
import java.util.Optional;

import com.paytech.foundation.idempotency.api.CommandIdempotencyService.CommandFailure;
import com.paytech.foundation.idempotency.api.CommandIdempotencyService.CommandRecord;
import com.paytech.foundation.idempotency.api.CommandIdempotencyService.CommandSuccess;
import com.paytech.foundation.idempotency.api.CommandIdempotencyService.NewCommandClaim;

/** 命令幂等机制内部的服务本地持久化边界。 */
public interface CommandIdempotencyRepository {

    CommandRecord claim(NewCommandClaim claim);

    Optional<CommandRecord> find(String callerScope, String operationType, String idempotencyKey);

    boolean acquireOrRenewLease(long commandIdempotencyId, long expectedVersion, String expectedStage, Instant now,
        String newLockOwner, Instant newLockUntil);

    void advanceStage(long commandIdempotencyId, String operationType, long expectedVersion, String expectedStage,
        String nextStage, String lockOwner, Instant newLockUntil, Instant updatedAt);

    void completeSuccess(CommandSuccess completion);

    void completeFailure(CommandFailure failure);
}
