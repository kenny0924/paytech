package com.paytech.foundation.idempotency.auto;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.idempotency.api.CommandIdempotencyService.CommandRecord;
import com.paytech.foundation.idempotency.api.CommandIdempotencyService.CommandSuccess;
import com.paytech.foundation.idempotency.api.CommandIdempotencyService.NewCommandClaim;
import com.paytech.foundation.idempotency.api.CommandProcessStatus;
import com.paytech.foundation.idempotency.persistence.mapper.CommandIdempotencyMapper;
import com.paytech.foundation.idempotency.persistence.mapper.CommandIdempotencyRow;
import com.paytech.foundation.idempotency.persistence.repository.CommandIdempotencyRepositoryImpl;

class CommandIdempotencyRepositoryAutoTest {

    private static final Instant NOW = Instant.parse("2026-09-09T10:00:00Z");

    private final StubMapper mapper = new StubMapper();
    private final CommandIdempotencyRepositoryImpl repository = new CommandIdempotencyRepositoryImpl(mapper);

    @Test
    void test_claimInsertsProcessingCandidateAndReturnsWinner() {
        mapper.selected = row("SUCCEEDED");
        NewCommandClaim claim = new NewCommandClaim(11L, "CLIENT:7", "CREATE_CHECKOUT", "key-1", "hash", 1, 1, "{}",
            NOW, "CLAIMED", "ORDER", 21L, "ord-1", "node-1", NOW.plusSeconds(30), NOW);

        CommandRecord winner = repository.claim(claim);

        assertEquals("PROCESSING", mapper.inserted.processStatus());
        assertEquals(1L, mapper.inserted.version());
        assertEquals(CommandProcessStatus.SUCCEEDED, winner.processStatus());
    }

    @Test
    void test_claimFailsWhenWinnerCannotBeRead() {
        assertThrows(ErrorException.class,
            () -> repository.claim(new NewCommandClaim(11L, "CLIENT:7", "CREATE_CHECKOUT", "key-1", "hash", 1, 1, "{}",
                NOW, "CLAIMED", "ORDER", 21L, "ord-1", "node-1", NOW.plusSeconds(30), NOW)));
    }

    @Test
    void test_leaseReturnsCasOutcome() {
        mapper.affectedRows = 0;
        assertFalse(repository.acquireOrRenewLease(11L, 1L, "CLAIMED", NOW, "node-1", NOW.plusSeconds(30)));

        mapper.affectedRows = 1;
        assertTrue(repository.acquireOrRenewLease(11L, 1L, "CLAIMED", NOW, "node-1", NOW.plusSeconds(30)));
    }

    @Test
    void test_stageAndCompletionRejectLostCas() {
        mapper.affectedRows = 0;
        assertThrows(ConcurrentRequestException.class, () -> repository.advanceStage(11L, "CREATE_CHECKOUT", 1L,
            "CLAIMED", "ORDER_COMMITTED", "node-1", NOW.plusSeconds(30), NOW));
        assertThrows(ConcurrentRequestException.class, () -> repository.completeSuccess(new CommandSuccess(11L,
            "CREATE_CHECKOUT", 1L, "CLAIMED", "COMPLETED", "node-1", "CREATED", 201, 1, "/orders/1", "{}", NOW)));
    }

    @Test
    void test_unknownPersistedStatusFailsAsSystemError() {
        mapper.selected = row("UNKNOWN");
        assertThrows(ErrorException.class, () -> repository.find("CLIENT:7", "CREATE_CHECKOUT", "key-1"));
    }

    private CommandIdempotencyRow row(String status) {
        return new CommandIdempotencyRow(11L, "CLIENT:7", "CREATE_CHECKOUT", "key-1", "hash", 1, 1, "{}", NOW,
            "COMPLETED", status, "ORDER", 21L, "ord-1", "CREATED", 201, 1, "/orders/1", "{}", null, null, 2L, NOW, NOW);
    }

    private static final class StubMapper implements CommandIdempotencyMapper {

        private int affectedRows = 1;
        private CommandIdempotencyRow inserted;
        private CommandIdempotencyRow selected;

        @Override
        public int insertOrKeepWinner(CommandIdempotencyRow record) {
            inserted = record;
            return affectedRows;
        }

        @Override
        public CommandIdempotencyRow selectByIdentityForUpdate(String callerScope, String operationType,
            String idempotencyKey) {
            return selected;
        }

        @Override
        public CommandIdempotencyRow selectByIdentity(String callerScope, String operationType, String idempotencyKey) {
            return selected;
        }

        @Override
        public int acquireOrRenewLease(long commandIdempotencyId, long expectedVersion, String expectedStage,
            Instant now, String newLockOwner, Instant newLockUntil) {
            return affectedRows;
        }

        @Override
        public int advanceStage(long commandIdempotencyId, String operationType, long expectedVersion,
            String expectedStage, String nextStage, String lockOwner, Instant newLockUntil, Instant updatedAt) {
            return affectedRows;
        }

        @Override
        public int complete(long commandIdempotencyId, String operationType, long expectedVersion, String expectedStage,
            String completedStage, String completedStatus, String lockOwner, String responseCode,
            int responseHttpStatus, Integer responseSnapshotSchemaVersion, String responseLocation,
            String responseBodyJson, Instant completedAt) {
            return affectedRows;
        }
    }
}
