package com.paytech.risk.auto.ext.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.paytech.risk.api.decision.DecisionReleaseManagementModels.TestReleaseDetailResponseBody;
import com.paytech.risk.api.decision.DecisionReleaseManagementModels.ValidateTestReleaseRequestBody;
import com.paytech.risk.api.decision.DecisionReleaseManagementModels.ValidateTestReleaseResponseBody;
import com.paytech.risk.decision.domainservice.port.DecisionIdentifierGenerator;
import com.paytech.risk.test.base.RiskServiceAutoTestBase;
import com.paytech.risk.test.decision.DecisionReleaseTestFixture;
import com.paytech.risk.test.decision.DecisionReleaseTestFixture.CommandFacts;
import com.paytech.risk.test.decision.DecisionReleaseTestFixture.ReleaseFacts;
import com.paytech.risk.test.decision.RiskDecisionHttpClient;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** 验证 Validate 两事务、精确 post-A 恢复及不可达接管状态的真实 MySQL 协议。 */
class DecisionReleaseValidationRecoveryAutoTest extends RiskServiceAutoTestBase {

    private static final List<String> CHECK_ORDER = List.of(
        "REFERENCE_INTEGRITY",
        "AST_SHAPE",
        "AST_TYPES",
        "FEATURE_DEPENDENCY",
        "HANDLER_HASH",
        "ACTION_PARAMETERS",
        "TERMINAL_ACTION",
        "SYSTEM_OBLIGATION"
    );

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DecisionIdentifierGenerator identifiers;

    @Test
    void test_firstValidateCommitsExactlyEightChecksAndReplaysStableResponse() throws Exception {
        try (DecisionReleaseTestFixture fixture = fixture()) {
            var candidate = fixture.arrangeFrozenCandidate();
            String key = fixture.nextKey();

            HttpResponse<String> first = fixture.postValidate(
                candidate, DecisionReleaseTestFixture.ACTOR, key, 1);

            assertPassed(fixture, candidate, first);
            ReleaseFacts completed = fixture.releaseFacts(candidate);
            assertEquals(new ReleaseFacts("VALIDATING", 2, candidate.created().contentHash(), 2, 8), completed);
            assertEquals(CHECK_ORDER, fixture.checkCodes(candidate));
            CommandFacts command = fixture.commandFacts(DecisionReleaseTestFixture.ACTOR, key);
            assertEquals("SUCCEEDED", command.status());
            assertEquals("release:" + candidate.created().releaseId(), command.responseReference());

            HttpResponse<String> replay = fixture.postValidate(
                candidate, DecisionReleaseTestFixture.ACTOR, key, 1);
            assertEquals(200, replay.statusCode(), replay.body());
            assertEquals(first.body(), replay.body());
            assertEquals(completed, fixture.releaseFacts(candidate));
            assertEquals(command, fixture.commandFacts(DecisionReleaseTestFixture.ACTOR, key));

            HttpResponse<String> detailResponse = fixture.getCandidate(candidate);
            assertEquals(200, detailResponse.statusCode(), detailResponse.body());
            TestReleaseDetailResponseBody detail = fixture.body(
                detailResponse, TestReleaseDetailResponseBody.class);
            assertEquals(Boolean.TRUE, detail.allStaticChecksPassed());
            assertTrue(detail.waitingForShadow());
            assertEquals(2, detail.stateChanges().size());
            assertEquals(CHECK_ORDER, detail.staticChecks().stream().map(value -> value.checkCode()).toList());
        }
    }

    @Test
    void test_exactPostTransactionAStateRecoversWithOriginalCommand() throws Exception {
        try (DecisionReleaseTestFixture fixture = fixture()) {
            var candidate = fixture.arrangeFrozenCandidate();
            String key = fixture.nextKey();
            fixture.seedPostTransactionA(candidate, DecisionReleaseTestFixture.ACTOR, key, 1,
                DecisionReleaseTestFixture.ACTOR_ID, null);
            assertEquals(new ReleaseFacts("VALIDATING", 2, candidate.created().contentHash(), 2, 0),
                fixture.releaseFacts(candidate));
            assertEquals("PROCESSING", fixture.commandFacts(DecisionReleaseTestFixture.ACTOR, key).status());

            HttpResponse<String> response = fixture.postValidate(
                candidate, DecisionReleaseTestFixture.ACTOR, key, 1);

            assertPassed(fixture, candidate, response);
            assertEquals(new ReleaseFacts("VALIDATING", 2, candidate.created().contentHash(), 2, 8),
                fixture.releaseFacts(candidate));
            assertEquals("SUCCEEDED", fixture.commandFacts(DecisionReleaseTestFixture.ACTOR, key).status());
        }
    }

    @Test
    void test_sameOriginalCommandRecoversConcurrentlyWithoutDuplicateEvidence() throws Exception {
        try (DecisionReleaseTestFixture fixture = fixture();
             RiskDecisionHttpClient firstClient = new RiskDecisionHttpClient(port);
             RiskDecisionHttpClient secondClient = new RiskDecisionHttpClient(port)) {
            var candidate = fixture.arrangeFrozenCandidate();
            String key = fixture.nextKey();
            fixture.seedPostTransactionA(candidate, DecisionReleaseTestFixture.ACTOR, key, 1,
                DecisionReleaseTestFixture.ACTOR_ID, null);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<HttpResponse<String>> first = executor.submit(() -> postConcurrentValidate(
                    firstClient, candidate, key, ready, start));
                Future<HttpResponse<String>> second = executor.submit(() -> postConcurrentValidate(
                    secondClient, candidate, key, ready, start));
                assertTrue(ready.await(5, TimeUnit.SECONDS), "两个 HTTP 调用未能同步就绪");
                start.countDown();

                HttpResponse<String> firstResponse = first.get(10, TimeUnit.SECONDS);
                HttpResponse<String> secondResponse = second.get(10, TimeUnit.SECONDS);
                String persistedEvidence = "; releaseFacts=" + fixture.releaseFacts(candidate)
                    + "; commandFacts=" + fixture.commandFacts(DecisionReleaseTestFixture.ACTOR, key);
                assertEquals(200, firstResponse.statusCode(), firstResponse.body() + persistedEvidence);
                assertEquals(200, secondResponse.statusCode(), secondResponse.body() + persistedEvidence);
                assertPassed(fixture, candidate, firstResponse);
                assertPassed(fixture, candidate, secondResponse);
                assertEquals(firstResponse.body(), secondResponse.body());
            } finally {
                start.countDown();
                executor.shutdownNow();
            }

            assertEquals(1, fixture.commandCount(DecisionReleaseTestFixture.ACTOR, key));
            assertEquals("SUCCEEDED", fixture.commandFacts(DecisionReleaseTestFixture.ACTOR, key).status());
            assertEquals(new ReleaseFacts("VALIDATING", 2, candidate.created().contentHash(), 2, 8),
                fixture.releaseFacts(candidate));
            assertEquals(CHECK_ORDER, fixture.checkCodes(candidate));
        }
    }

    @Test
    void test_processingHashForExpectedVersionTwoFailsClosed() throws Exception {
        try (DecisionReleaseTestFixture fixture = fixture()) {
            var candidate = fixture.arrangeFrozenCandidate();
            String key = fixture.nextKey();
            fixture.seedPostTransactionA(candidate, DecisionReleaseTestFixture.ACTOR, key, 2,
                DecisionReleaseTestFixture.ACTOR_ID, null);
            assertInternalErrorAndUnchanged(fixture, candidate, DecisionReleaseTestFixture.ACTOR, key, 2);
        }
    }

    @Test
    void test_secondProcessingKeyCannotTakeOverOriginalTransactionA() throws Exception {
        try (DecisionReleaseTestFixture fixture = fixture()) {
            var candidate = fixture.arrangeFrozenCandidate();
            String originalKey = fixture.nextKey();
            fixture.seedPostTransactionA(candidate, DecisionReleaseTestFixture.ACTOR, originalKey, 1,
                DecisionReleaseTestFixture.ACTOR_ID, null);
            String takeoverKey = fixture.nextKey();
            fixture.insertProcessingCommand(candidate, DecisionReleaseTestFixture.ACTOR, takeoverKey, 1);
            CommandFacts originalBefore = fixture.commandFacts(DecisionReleaseTestFixture.ACTOR, originalKey);

            assertInternalErrorAndUnchanged(
                fixture, candidate, DecisionReleaseTestFixture.ACTOR, takeoverKey, 1);
            assertEquals(originalBefore, fixture.commandFacts(DecisionReleaseTestFixture.ACTOR, originalKey));
        }
    }

    @Test
    void test_processingActorMustMatchStartedStateActor() throws Exception {
        try (DecisionReleaseTestFixture fixture = fixture()) {
            var candidate = fixture.arrangeFrozenCandidate();
            String key = fixture.nextKey();
            fixture.seedPostTransactionA(candidate, DecisionReleaseTestFixture.OTHER_ACTOR, key, 1,
                DecisionReleaseTestFixture.ACTOR_ID, null);

            assertInternalErrorAndUnchanged(
                fixture, candidate, DecisionReleaseTestFixture.OTHER_ACTOR, key, 1);
        }
    }

    @Test
    void test_processingTriggerMustMatchOriginalIdempotencyKey() throws Exception {
        try (DecisionReleaseTestFixture fixture = fixture()) {
            var candidate = fixture.arrangeFrozenCandidate();
            String key = fixture.nextKey();
            String wrongTrigger = "f".repeat(64);
            var command = fixture.seedPostTransactionA(candidate, DecisionReleaseTestFixture.ACTOR, key, 1,
                DecisionReleaseTestFixture.ACTOR_ID, wrongTrigger);
            DecisionReleaseTestFixture.requireDifferentTrigger(command.triggerId(), wrongTrigger);

            assertInternalErrorAndUnchanged(
                fixture, candidate, DecisionReleaseTestFixture.ACTOR, key, 1);
        }
    }

    private static void assertPassed(
        DecisionReleaseTestFixture fixture,
        DecisionReleaseTestFixture.Candidate candidate,
        HttpResponse<String> response
    ) {
        assertEquals(200, response.statusCode(), response.body() + "; releaseFacts="
            + fixture.releaseFacts(candidate));
        ValidateTestReleaseResponseBody validated = fixture.body(
            response, ValidateTestReleaseResponseBody.class);
        assertEquals(candidate.created().releaseId(), validated.releaseId());
        assertEquals(candidate.created().releaseNo(), validated.releaseNo());
        assertEquals("TEST", validated.environmentCode());
        assertEquals("VALIDATING", validated.status());
        assertEquals(2, validated.version());
        assertEquals(candidate.created().contentHash(), validated.contentHash());
        assertTrue(validated.allStaticChecksPassed());
        assertEquals(CHECK_ORDER, validated.checks().stream().map(value -> value.checkCode()).toList());
        validated.checks().forEach(check -> {
            assertEquals("PASSED", check.checkStatus());
            assertEquals("INFO", check.severity());
            assertNull(check.errorCode());
            assertNull(check.errorSummary());
        });
    }

    private static HttpResponse<String> postConcurrentValidate(
        RiskDecisionHttpClient client,
        DecisionReleaseTestFixture.Candidate candidate,
        String key,
        CountDownLatch ready,
        CountDownLatch start
    ) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("并发 Validate 未收到起跑信号");
        }
        return client.post("/decision-releases/v1/candidates/" + candidate.created().releaseId() + "/validate",
            DecisionReleaseTestFixture.ACTOR, DecisionReleaseTestFixture.SESSION, key,
            new ValidateTestReleaseRequestBody(1));
    }

    private static void assertInternalErrorAndUnchanged(
        DecisionReleaseTestFixture fixture,
        DecisionReleaseTestFixture.Candidate candidate,
        String actor,
        String key,
        int expectedVersion
    ) throws Exception {
        ReleaseFacts releaseBefore = fixture.releaseFacts(candidate);
        CommandFacts commandBefore = fixture.commandFacts(actor, key);

        HttpResponse<String> response = fixture.postValidate(candidate, actor, key, expectedVersion);

        RiskDecisionHttpClient.assertProblem(response, 500, "INTERNAL_ERROR");
        assertFalse(response.body().contains(key));
        assertEquals(releaseBefore, fixture.releaseFacts(candidate));
        assertEquals(commandBefore, fixture.commandFacts(actor, key));
    }

    private DecisionReleaseTestFixture fixture() {
        return new DecisionReleaseTestFixture(port, decisionTestMapper, jdbc, transactionManager, identifiers);
    }
}
