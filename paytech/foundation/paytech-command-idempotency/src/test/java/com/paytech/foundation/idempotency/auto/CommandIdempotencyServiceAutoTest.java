package com.paytech.foundation.idempotency.auto;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.idempotency.api.CommandIdempotencyService.CommandFailure;
import com.paytech.foundation.idempotency.api.CommandIdempotencyService.CommandRecord;
import com.paytech.foundation.idempotency.api.CommandIdempotencyService.CommandSuccess;
import com.paytech.foundation.idempotency.api.CommandIdempotencyService.NewCommandClaim;
import com.paytech.foundation.idempotency.api.CommandProcessStatus;
import com.paytech.foundation.idempotency.persistence.repository.CommandIdempotencyRepository;
import com.paytech.foundation.idempotency.service.CommandIdempotencyServiceImpl;

class CommandIdempotencyServiceAutoTest {

    private static final Instant NOW = Instant.parse("2026-09-09T10:00:00Z");

    private final StubRepository repository = new StubRepository();
    private final CommandIdempotencyServiceImpl service = new CommandIdempotencyServiceImpl(repository);

    @AfterEach
    void clearTransactionState() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void test_findDoesNotRequireTransaction() {
        repository.record = record(CommandProcessStatus.PROCESSING);

        Optional<CommandRecord> result = service.find("CLIENT:7", "CREATE_CHECKOUT", "key-1");

        assertEquals(repository.record, result.orElseThrow());
    }

    @Test
    void test_claimRequiresCallerOwnedWritableTransaction() {
        NewCommandClaim claim = claim();

        assertThrows(ErrorException.class, () -> service.claim(claim));

        repository.record = record(CommandProcessStatus.PROCESSING);
        CommandRecord winner = inWritableTransaction(() -> service.claim(claim));
        assertSame(repository.record, winner);
        assertSame(claim, repository.claim);
    }

    @Test
    void test_writeRejectsReadOnlyTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);

        assertThrows(ErrorException.class,
            () -> service.acquireOrRenewLease(11L, 1L, "CLAIMED", NOW, "node-1", NOW.plusSeconds(30)));
    }

    @Test
    void test_completionStatusRangesAreExplicit() {
        CommandSuccess success = new CommandSuccess(11L, "CREATE_CHECKOUT", 1L, "CLAIMED", "COMPLETED", "node-1",
            "CREATED", 409, null, null, null, NOW);
        CommandFailure failure = new CommandFailure(11L, "CREATE_CHECKOUT", 1L, "CLAIMED", "COMPLETED", "node-1",
            "FAILED", 503, null, null, null, NOW);

        assertThrows(IllegalArgumentException.class, () -> inWritableTransaction(() -> {
            service.completeSuccess(success);
            return null;
        }));
        assertThrows(IllegalArgumentException.class, () -> inWritableTransaction(() -> {
            service.completeFailure(failure);
            return null;
        }));
    }

    private NewCommandClaim claim() {
        return new NewCommandClaim(11L, "CLIENT:7", "CREATE_CHECKOUT", "key-1", "hash", 1, 1, "{}", NOW, "CLAIMED",
            "ORDER", 21L, "ord-1", "node-1", NOW.plusSeconds(30), NOW);
    }

    private CommandRecord record(CommandProcessStatus status) {
        return new CommandRecord(11L, "CLIENT:7", "CREATE_CHECKOUT", "key-1", "hash", 1, 1, "{}", NOW, "CLAIMED",
            status, "ORDER", 21L, "ord-1", null, null, null, null, null, "node-1", NOW.plusSeconds(30), 1L, NOW, NOW);
    }

    private <T> T inWritableTransaction(Supplier<T> action) {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
        try {
            return action.get();
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }

    private static final class StubRepository implements CommandIdempotencyRepository {

        private NewCommandClaim claim;
        private CommandRecord record;

        @Override
        public CommandRecord claim(NewCommandClaim value) {
            claim = value;
            return record;
        }

        @Override
        public Optional<CommandRecord> find(String callerScope, String operationType, String idempotencyKey) {
            return Optional.ofNullable(record);
        }

        @Override
        public boolean acquireOrRenewLease(long commandIdempotencyId, long expectedVersion, String expectedStage,
            Instant now, String newLockOwner, Instant newLockUntil) {
            return true;
        }

        @Override
        public void advanceStage(long commandIdempotencyId, String operationType, long expectedVersion,
            String expectedStage, String nextStage, String lockOwner, Instant newLockUntil, Instant updatedAt) {}

        @Override
        public void completeSuccess(CommandSuccess completion) {}

        @Override
        public void completeFailure(CommandFailure failure) {}
    }
}
