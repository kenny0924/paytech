package com.paytech.risk.auto.ext.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.paytech.risk.api.decision.response.TestReleaseDetailResponseBody;
import com.paytech.risk.api.decision.request.ValidateTestReleaseRequestBody;
import com.paytech.risk.api.decision.response.ValidateTestReleaseResponseBody;
import com.paytech.risk.decision.domainservice.port.DecisionIdentifierGenerator;
import com.paytech.risk.test.base.RiskServiceAutoTestBase;
import com.paytech.risk.test.decision.DecisionReleaseTestFixture;
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

/**
 * 验证 Validate 两事务、开始后中断的恢复及恢复身份校验的真实 MySQL 协议
 * <li>恢复由 Candidate 自身状态驱动：已开始未完成时，原操作者以原 expectedVersion 重入完成原校验。</li>
 * <li>HTTP Idempotency-Key 归 Gateway，本服务不再按请求 Key 记录或比对命令。</li>
 *
 * @see com.paytech.risk.api.decision.DecisionReleaseManagementApi
 */
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
            assertEquals(new ReleaseFacts("VALIDATING", 2, candidate.created().getContentHash(), 2, 8), completed);
            assertEquals(CHECK_ORDER, fixture.checkCodes(candidate));

            HttpResponse<String> replay = fixture.postValidate(
                candidate, DecisionReleaseTestFixture.ACTOR, key, 1);
            assertEquals(200, replay.statusCode(), replay.body());
            assertEquals(first.body(), replay.body());
            assertEquals(completed, fixture.releaseFacts(candidate));

            HttpResponse<String> detailResponse = fixture.getCandidate(candidate);
            assertEquals(200, detailResponse.statusCode(), detailResponse.body());
            TestReleaseDetailResponseBody detail = fixture.body(
                detailResponse, TestReleaseDetailResponseBody.class);
            assertEquals(Boolean.TRUE, detail.getAllStaticChecksPassed());
            assertTrue(detail.getWaitingForShadow());
            assertEquals(2, detail.getStateChanges().size());
            assertEquals(CHECK_ORDER, detail.getStaticChecks().stream().map(value -> value.getCheckCode()).toList());
        }
    }

    @Test
    void test_startedValidationRecoversForOriginalActor() throws Exception {
        try (DecisionReleaseTestFixture fixture = fixture()) {
            var candidate = fixture.arrangeFrozenCandidate();
            String key = fixture.nextKey();
            fixture.seedValidationStarted(candidate, DecisionReleaseTestFixture.ACTOR_ID);
            assertEquals(new ReleaseFacts("VALIDATING", 2, candidate.created().getContentHash(), 2, 0),
                fixture.releaseFacts(candidate));

            HttpResponse<String> response = fixture.postValidate(
                candidate, DecisionReleaseTestFixture.ACTOR, key, 1);

            assertPassed(fixture, candidate, response);
            assertEquals(new ReleaseFacts("VALIDATING", 2, candidate.created().getContentHash(), 2, 8),
                fixture.releaseFacts(candidate));
        }
    }

    @Test
    void test_concurrentRecoveryCompletesOnceWithoutDuplicateEvidence() throws Exception {
        try (DecisionReleaseTestFixture fixture = fixture();
             RiskDecisionHttpClient firstClient = new RiskDecisionHttpClient(port);
             RiskDecisionHttpClient secondClient = new RiskDecisionHttpClient(port)) {
            var candidate = fixture.arrangeFrozenCandidate();
            String key = fixture.nextKey();
            fixture.seedValidationStarted(candidate, DecisionReleaseTestFixture.ACTOR_ID);
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
                String persistedEvidence = "; releaseFacts=" + fixture.releaseFacts(candidate);
                assertEquals(200, firstResponse.statusCode(), firstResponse.body() + persistedEvidence);
                assertEquals(200, secondResponse.statusCode(), secondResponse.body() + persistedEvidence);
                assertPassed(fixture, candidate, firstResponse);
                assertPassed(fixture, candidate, secondResponse);
                assertEquals(firstResponse.body(), secondResponse.body());
            } finally {
                start.countDown();
                executor.shutdownNow();
            }

            assertEquals(new ReleaseFacts("VALIDATING", 2, candidate.created().getContentHash(), 2, 8),
                fixture.releaseFacts(candidate));
            assertEquals(CHECK_ORDER, fixture.checkCodes(candidate));
        }
    }

    @Test
    void test_recoveryWithDifferentExpectedVersionIsRejected() throws Exception {
        try (DecisionReleaseTestFixture fixture = fixture()) {
            var candidate = fixture.arrangeFrozenCandidate();
            fixture.seedValidationStarted(candidate, DecisionReleaseTestFixture.ACTOR_ID);
            assertConflictAndUnchanged(fixture, candidate, DecisionReleaseTestFixture.ACTOR, fixture.nextKey(), 2);
        }
    }

    @Test
    void test_recoveryByDifferentActorIsRejected() throws Exception {
        try (DecisionReleaseTestFixture fixture = fixture()) {
            var candidate = fixture.arrangeFrozenCandidate();
            fixture.seedValidationStarted(candidate, DecisionReleaseTestFixture.ACTOR_ID);
            assertConflictAndUnchanged(fixture, candidate, DecisionReleaseTestFixture.OTHER_ACTOR, fixture.nextKey(), 1);
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
        assertEquals(candidate.created().getReleaseId(), validated.getReleaseId());
        assertEquals(candidate.created().getReleaseNo(), validated.getReleaseNo());
        assertEquals("TEST", validated.getEnvironmentCode());
        assertEquals("VALIDATING", validated.getStatus());
        assertEquals(2, validated.getVersion());
        assertEquals(candidate.created().getContentHash(), validated.getContentHash());
        assertTrue(validated.getAllStaticChecksPassed());
        assertEquals(CHECK_ORDER, validated.getChecks().stream().map(value -> value.getCheckCode()).toList());
        validated.getChecks().forEach(check -> {
            assertEquals("PASSED", check.getCheckStatus());
            assertEquals("INFO", check.getSeverity());
            assertNull(check.getErrorCode());
            assertNull(check.getErrorSummary());
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
        return client.post("/decision-releases/v1/candidates/" + candidate.created().getReleaseId() + "/validate",
            DecisionReleaseTestFixture.ACTOR, DecisionReleaseTestFixture.SESSION, key,
            new ValidateTestReleaseRequestBody(1));
    }

    /** 恢复身份不符时返回并发冲突，Candidate 状态与审计不变。 */
    private static void assertConflictAndUnchanged(
        DecisionReleaseTestFixture fixture,
        DecisionReleaseTestFixture.Candidate candidate,
        String actor,
        String key,
        int expectedVersion
    ) throws Exception {
        ReleaseFacts releaseBefore = fixture.releaseFacts(candidate);

        HttpResponse<String> response = fixture.postValidate(candidate, actor, key, expectedVersion);

        RiskDecisionHttpClient.assertProblem(response, 409, "CONCURRENT_REQUEST");
        assertEquals(releaseBefore, fixture.releaseFacts(candidate));
    }

    private DecisionReleaseTestFixture fixture() {
        return new DecisionReleaseTestFixture(port, decisionTestMapper, jdbc, transactionManager, identifiers);
    }
}
