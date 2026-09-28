package com.paytech.foundation.idempotency.service;

import java.time.Instant;
import java.util.Optional;

import com.paytech.foundation.idempotency.api.CommandIdempotencyService;
import com.paytech.foundation.idempotency.persistence.repository.CommandIdempotencyRepository;

import lombok.AllArgsConstructor;

/** 不创建事务的命令幂等默认实现。 */
@AllArgsConstructor
public final class CommandIdempotencyServiceImpl implements CommandIdempotencyService {

    private static final CommandIdempotencyValidator VALIDATOR = new CommandIdempotencyValidator();

    private final CommandIdempotencyRepository repository;

    @Override
    public CommandRecord claim(NewCommandClaim claim) {
        VALIDATOR.validateClaim(claim);
        VALIDATOR.requireWritableTransaction();
        return repository.claim(claim);
    }

    @Override
    public Optional<CommandRecord> find(String callerScope, String operationType, String idempotencyKey) {
        VALIDATOR.validateIdentity(callerScope, operationType, idempotencyKey);
        return repository.find(callerScope, operationType, idempotencyKey);
    }

    @Override
    public boolean acquireOrRenewLease(long commandIdempotencyId, long expectedVersion, String expectedStage,
        Instant now, String newLockOwner, Instant newLockUntil) {
        VALIDATOR.validateLease(commandIdempotencyId, expectedVersion, expectedStage, now, newLockOwner, newLockUntil);
        VALIDATOR.requireWritableTransaction();
        return repository.acquireOrRenewLease(commandIdempotencyId, expectedVersion, expectedStage, now, newLockOwner,
            newLockUntil);
    }

    @Override
    public void advanceStage(long commandIdempotencyId, String operationType, long expectedVersion,
        String expectedStage, String nextStage, String lockOwner, Instant newLockUntil, Instant updatedAt) {
        VALIDATOR.validateTransition(commandIdempotencyId, operationType, expectedVersion, expectedStage, nextStage,
            lockOwner, newLockUntil, updatedAt);
        VALIDATOR.requireWritableTransaction();
        repository.advanceStage(commandIdempotencyId, operationType, expectedVersion, expectedStage, nextStage,
            lockOwner, newLockUntil, updatedAt);
    }

    @Override
    public void completeSuccess(CommandSuccess completion) {
        VALIDATOR.validateSuccess(completion);
        VALIDATOR.requireWritableTransaction();
        repository.completeSuccess(completion);
    }

    @Override
    public void completeFailure(CommandFailure failure) {
        VALIDATOR.validateFailure(failure);
        VALIDATOR.requireWritableTransaction();
        repository.completeFailure(failure);
    }
}
