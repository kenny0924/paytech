package com.paytech.risk.auto.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.paytech.risk.decision.domain.enums.DecisionEnvironment;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;
import com.paytech.risk.decision.domain.enums.StaticCheckCode;
import com.paytech.risk.decision.domain.enums.StaticCheckSeverity;
import com.paytech.risk.decision.domain.enums.StaticCheckStatus;
import com.paytech.risk.decision.domain.release.DecisionReleaseAggregate;
import com.paytech.risk.decision.domain.release.DecisionReleaseAggregate.FrozenCompositionView;
import com.paytech.risk.decision.domain.release.DecisionReleaseAggregate.ReleaseRootView;
import com.paytech.risk.decision.domain.release.DecisionReleaseAggregate.ReleaseStateChange;
import com.paytech.risk.decision.domain.release.DecisionReleaseAggregate.StaticCheckEvidence;
import com.paytech.risk.decision.domain.release.DecisionReleaseStaticValidator.DecisionReleaseClosure;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** TEST Candidate 可观察状态与不可变 Audit 证据的纯领域测试。 */
class DecisionReleaseAggregateAutoTest {

    private static final String HASH = "b".repeat(64);
    private static final Instant NOW = Instant.parse("2026-09-05T00:00:00Z");

    @Test
    void test_acceptsDraftAndValidatingZeroCheckStates() {
        DecisionReleaseAggregate draft = aggregate(DecisionReleaseStatus.DRAFT, 1,
            List.of(initial()), List.of());
        assertNull(draft.allStaticChecksPassed());
        assertFalse(draft.waitingForShadow());

        DecisionReleaseAggregate validating = aggregate(DecisionReleaseStatus.VALIDATING, 2,
            List.of(initial(), started()), List.of());
        assertNull(validating.allStaticChecksPassed());
        assertFalse(validating.waitingForShadow());
    }

    @Test
    void test_acceptsExactlyEightPassedChecksAndDerivesWaitingForShadow() {
        List<StaticCheckEvidence> mutable = new ArrayList<>(checks(null));
        DecisionReleaseAggregate aggregate = aggregate(DecisionReleaseStatus.VALIDATING, 2,
            List.of(initial(), started()), mutable);
        mutable.clear();

        assertEquals(8, aggregate.staticChecks().size());
        assertEquals(Boolean.TRUE, aggregate.allStaticChecksPassed());
        assertTrue(aggregate.waitingForShadow());
        assertThrows(UnsupportedOperationException.class, () -> aggregate.staticChecks().clear());
    }

    @Test
    void test_acceptsRejectedOnlyWithEightChecksAndFailureEvent() {
        DecisionReleaseAggregate aggregate = aggregate(DecisionReleaseStatus.REJECTED, 3,
            List.of(initial(), started(), rejected()), checks(StaticCheckCode.AST_TYPES));

        assertEquals(Boolean.FALSE, aggregate.allStaticChecksPassed());
        assertFalse(aggregate.waitingForShadow());
    }

    @Test
    void test_rejectsPartialDuplicateOrWrongOrderChecks() {
        assertThrows(IllegalArgumentException.class, () -> aggregate(DecisionReleaseStatus.VALIDATING, 2,
            List.of(initial(), started()), checks(null).subList(0, 7)));

        List<StaticCheckEvidence> duplicate = new ArrayList<>(checks(null));
        duplicate.set(1, duplicate.getFirst());
        assertThrows(IllegalArgumentException.class, () -> aggregate(DecisionReleaseStatus.VALIDATING, 2,
            List.of(initial(), started()), duplicate));

        List<StaticCheckEvidence> reversed = new ArrayList<>(checks(null));
        StaticCheckEvidence first = reversed.get(0);
        reversed.set(0, reversed.get(1));
        reversed.set(1, first);
        assertThrows(IllegalArgumentException.class, () -> aggregate(DecisionReleaseStatus.VALIDATING, 2,
            List.of(initial(), started()), reversed));
    }

    @Test
    void test_rejectsStatusVersionEventAndCheckMatrixCorruption() {
        assertThrows(IllegalArgumentException.class, () -> aggregate(DecisionReleaseStatus.DRAFT, 2,
            List.of(initial()), List.of()));
        assertThrows(IllegalArgumentException.class, () -> aggregate(DecisionReleaseStatus.SHADOWING, 3,
            List.of(initial(), started()), checks(null)));
        assertThrows(IllegalArgumentException.class, () -> aggregate(DecisionReleaseStatus.REJECTED, 3,
            List.of(initial(), started()), checks(StaticCheckCode.AST_TYPES)));
        assertThrows(IllegalArgumentException.class, () -> aggregate(DecisionReleaseStatus.VALIDATING, 2,
            List.of(initial()), List.of()));

        List<StaticCheckEvidence> broken = new ArrayList<>(checks(null));
        broken.set(0, new StaticCheckEvidence(901L, NOW, StaticCheckCode.REFERENCE_INTEGRITY,
            StaticCheckStatus.PASSED, StaticCheckSeverity.ERROR, "BAD", "bad"));
        assertThrows(IllegalArgumentException.class, () -> aggregate(DecisionReleaseStatus.VALIDATING, 2,
            List.of(initial(), started()), broken));

        ReleaseStateChange missingActor = new ReleaseStateChange(811L, NOW, null, DecisionReleaseStatus.DRAFT,
            "CANDIDATE_CREATED", "OPERATIONS_COMMAND", "1".repeat(64), null, 1);
        assertThrows(IllegalArgumentException.class, () -> aggregate(DecisionReleaseStatus.DRAFT, 1,
            List.of(missingActor), List.of()));
    }

    private static DecisionReleaseAggregate aggregate(
        DecisionReleaseStatus status,
        int version,
        List<ReleaseStateChange> events,
        List<StaticCheckEvidence> checks
    ) {
        ReleaseRootView root = new ReleaseRootView(801L, 1, 201L, "CHECKOUT_PREPARE", 202L, 301L,
            DecisionEnvironment.TEST, status, version, HASH, 11L, NOW);
        FrozenCompositionView composition = new FrozenCompositionView(201L, "CHECKOUT_PREPARE", 202L, 1,
            "a".repeat(64), 301L, "RULE_SET", 1, "c".repeat(64));
        return new DecisionReleaseAggregate(root, composition, events, checks, emptyClosure());
    }

    private static ReleaseStateChange initial() {
        return new ReleaseStateChange(811L, NOW, null, DecisionReleaseStatus.DRAFT, "CANDIDATE_CREATED",
            "OPERATIONS_COMMAND", "1".repeat(64), 11L, 1);
    }

    private static ReleaseStateChange started() {
        return new ReleaseStateChange(812L, NOW.plusSeconds(1), DecisionReleaseStatus.DRAFT,
            DecisionReleaseStatus.VALIDATING, "STATIC_VALIDATION_STARTED", "OPERATIONS_COMMAND",
            "2".repeat(64), 11L, 2);
    }

    private static ReleaseStateChange rejected() {
        return new ReleaseStateChange(813L, NOW.plusSeconds(2), DecisionReleaseStatus.VALIDATING,
            DecisionReleaseStatus.REJECTED, "STATIC_VALIDATION_FAILED", "OPERATIONS_COMMAND",
            "2".repeat(64), 11L, 3);
    }

    private static List<StaticCheckEvidence> checks(StaticCheckCode failedCode) {
        List<StaticCheckEvidence> values = new ArrayList<>();
        int index = 0;
        for (StaticCheckCode code : StaticCheckCode.values()) {
            boolean failed = code == failedCode;
            values.add(new StaticCheckEvidence(901L + index, NOW.plusSeconds(index), code,
                failed ? StaticCheckStatus.FAILED : StaticCheckStatus.PASSED,
                failed ? StaticCheckSeverity.ERROR : StaticCheckSeverity.INFO,
                failed ? "STATIC_" + code.getCode() : null,
                failed ? "first=STATIC_" + code.getCode() + "@rules; violation_count=1" : null));
            index++;
        }
        return values;
    }

    private static DecisionReleaseClosure emptyClosure() {
        return new DecisionReleaseClosure(null, null, null, List.of());
    }
}
