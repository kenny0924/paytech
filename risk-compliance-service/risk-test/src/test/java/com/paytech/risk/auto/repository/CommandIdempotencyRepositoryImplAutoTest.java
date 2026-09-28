package com.paytech.risk.domainservice.repository.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.core.dal.dataobject.CommandIdempotency;
import com.paytech.risk.domain.enums.CommandIdempotencyStatus;
import com.paytech.risk.domain.enums.CommandOperationType;
import com.paytech.risk.domainservice.repository.CommandIdempotencyRepository.LockedCommandClaim;
import org.junit.jupiter.api.Test;

class CommandIdempotencyRepositoryImplAutoTest {

    @Test
    void candidate_identifier_distinguishes_first_claim_from_an_existing_locked_identity() {
        CommandIdempotency candidate = record(101L);
        LockedCommandClaim first = CommandIdempotencyRepositoryImpl.classifyLockedCandidate(
            candidate, record(101L));
        assertTrue(first.first());
        assertEquals(101L, first.record().getIdempotencyId());

        CommandIdempotency existing = record(202L);
        LockedCommandClaim replay = CommandIdempotencyRepositoryImpl.classifyLockedCandidate(candidate, existing);
        assertTrue(!replay.first());
        assertSame(existing, replay.record());
    }

    @Test
    void missing_target_or_candidate_identifier_collision_fails_closed() {
        CommandIdempotency candidate = record(101L);
        assertThrows(ErrorException.class,
            () -> CommandIdempotencyRepositoryImpl.classifyLockedCandidate(candidate, null));

        CommandIdempotency missingLockedId = record(202L);
        missingLockedId.setIdempotencyId(null);
        assertThrows(ErrorException.class,
            () -> CommandIdempotencyRepositoryImpl.classifyLockedCandidate(candidate, missingLockedId));

        CommandIdempotency collided = record(101L);
        collided.setCallerScope("another-scope");
        assertThrows(ErrorException.class,
            () -> CommandIdempotencyRepositoryImpl.classifyLockedCandidate(candidate, collided));
    }

    @Test
    void generated_identifier_must_be_positive_before_insert() {
        assertDoesNotThrow(() -> CommandIdempotencyRepositoryImpl.requirePositiveIdentifier(1L));
        assertThrows(ErrorException.class, () -> CommandIdempotencyRepositoryImpl.requirePositiveIdentifier(0L));
        assertThrows(ErrorException.class, () -> CommandIdempotencyRepositoryImpl.requirePositiveIdentifier(-1L));
    }

    private CommandIdempotency record(long idempotencyId) {
        Instant now = Instant.parse("2026-09-06T10:15:30.123456Z");
        CommandIdempotency record = new CommandIdempotency();
        record.setIdempotencyId(idempotencyId);
        record.setCallerScope("42");
        record.setOperationType(CommandOperationType.VALIDATE_TEST_DECISION_RELEASE);
        record.setIdempotencyKey("command-key");
        record.setRequestHash("a".repeat(64));
        record.setResponseRef(null);
        record.setStatus(CommandIdempotencyStatus.PROCESSING);
        record.setExpireTime(now.plusSeconds(24L * 60L * 60L));
        record.setCreateTime(now);
        record.setUpdateTime(now);
        return record;
    }
}
