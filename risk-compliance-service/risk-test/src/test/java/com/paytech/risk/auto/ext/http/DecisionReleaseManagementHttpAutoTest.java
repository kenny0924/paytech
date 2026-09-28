package com.paytech.risk.auto.ext.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.paytech.risk.api.decision.DecisionReleaseManagementModels.CreateTestReleaseRequestBody;
import com.paytech.risk.api.decision.DecisionReleaseManagementModels.TestReleaseDetailResponseBody;
import com.paytech.risk.decision.domainservice.port.DecisionIdentifierGenerator;
import com.paytech.risk.test.base.RiskServiceAutoTestBase;
import com.paytech.risk.test.decision.DecisionReleaseTestFixture;
import com.paytech.risk.test.decision.DecisionReleaseTestFixture.ReleaseFacts;
import com.paytech.risk.test.decision.RiskDecisionHttpClient;
import java.net.http.HttpResponse;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** 从真实 HTTP 入口验证 Candidate 创建、重放、列表与详情的 MySQL 事实。 */
class DecisionReleaseManagementHttpAutoTest extends RiskServiceAutoTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DecisionIdentifierGenerator identifiers;

    @Test
    void test_createReplaysStableCandidateAndPublishesListAndDetail() throws Exception {
        try (DecisionReleaseTestFixture fixture = fixture()) {
            var candidate = fixture.arrangeFrozenCandidate();
            var created = candidate.created();

            assertTrue(created.releaseNo() > 0);
            assertEquals("CHECKOUT_PREPARE", created.pointCode());
            assertEquals("TEST", created.environmentCode());
            assertEquals("DRAFT", created.status());
            assertEquals(1, created.version());
            assertEquals(64, created.contentHash().length());
            assertEquals(DecisionReleaseTestFixture.ACTOR, created.createdBy());
            assertNull(created.initialStateChange().fromStatus());
            assertEquals("DRAFT", created.initialStateChange().toStatus());
            assertEquals("CANDIDATE_CREATED", created.initialStateChange().reasonCode());
            assertEquals("OPERATIONS_COMMAND", created.initialStateChange().triggerType());
            assertEquals(1, created.initialStateChange().aggregateVersion());

            HttpResponse<String> replay = fixture.postCandidate(candidate.createKey(), candidate.request());
            assertEquals(200, replay.statusCode(), replay.body());
            assertEquals(candidate.firstBody(), replay.body());

            ReleaseFacts stored = fixture.releaseFacts(candidate);
            assertEquals(new ReleaseFacts("DRAFT", 1, created.contentHash(), 1, 0), stored);
            assertEquals(1, fixture.releaseCount());
            assertEquals(1, decisionTestMapper.ownedCommandCount(
                DecisionReleaseTestFixture.callerScope(DecisionReleaseTestFixture.ACTOR),
                "CREATE_TEST_DECISION_RELEASE", candidate.createKey()));
            assertEquals("SUCCEEDED", jdbc.queryForObject(
                "SELECT status FROM t_command_idempotency WHERE caller_scope=? AND operation_type=? "
                    + "AND BINARY idempotency_key=BINARY ?",
                String.class, DecisionReleaseTestFixture.callerScope(DecisionReleaseTestFixture.ACTOR),
                "CREATE_TEST_DECISION_RELEASE", candidate.createKey()));
            assertEquals("release:" + created.releaseId(), jdbc.queryForObject(
                "SELECT response_ref FROM t_command_idempotency WHERE caller_scope=? AND operation_type=? "
                    + "AND BINARY idempotency_key=BINARY ?",
                String.class, DecisionReleaseTestFixture.callerScope(DecisionReleaseTestFixture.ACTOR),
                "CREATE_TEST_DECISION_RELEASE", candidate.createKey()));

            HttpResponse<String> detailResponse = fixture.getCandidate(candidate);
            assertEquals(200, detailResponse.statusCode(), detailResponse.body());
            TestReleaseDetailResponseBody detail = fixture.body(
                detailResponse, TestReleaseDetailResponseBody.class);
            assertEquals(created.releaseId(), detail.releaseId());
            assertEquals(created.pointVersionId(), detail.frozenComposition().pointVersionId());
            assertEquals(created.ruleSetVersionId(), detail.frozenComposition().ruleSetVersionId());
            assertNull(detail.allStaticChecksPassed());
            assertFalse(detail.waitingForShadow());
            assertEquals(1, detail.stateChanges().size());
            assertTrue(detail.staticChecks().isEmpty());

            HttpResponse<String> listResponse = fixture.listDraftCandidates();
            assertEquals(200, listResponse.statusCode(), listResponse.body());
            var json = fixture.json(listResponse);
            assertEquals(1, json.get("page").intValue());
            assertEquals(10, json.get("size").intValue());
            assertTrue(json.get("total").longValue() >= 1);
            var item = StreamSupport.stream(json.get("items").spliterator(), false)
                .filter(value -> created.releaseId().equals(value.get("releaseId").textValue()))
                .findFirst().orElseThrow();
            assertEquals("DRAFT", item.get("status").textValue());
            assertTrue(item.get("allStaticChecksPassed").isNull());
        }
    }

    @Test
    void test_sameCandidateKeyWithDifferentHashReturnsConflictWithoutMutation() throws Exception {
        try (DecisionReleaseTestFixture fixture = fixture()) {
            var candidate = fixture.arrangeFrozenCandidate();
            ReleaseFacts before = fixture.releaseFacts(candidate);
            CreateTestReleaseRequestBody different = new CreateTestReleaseRequestBody(
                candidate.request().ruleSetVersionId(), "0".repeat(64));

            HttpResponse<String> response = fixture.postCandidate(candidate.createKey(), different);

            RiskDecisionHttpClient.assertProblem(response, 409, "CONCURRENT_REQUEST");
            assertEquals(before, fixture.releaseFacts(candidate));
            assertEquals(1, fixture.releaseCount());
        }
    }

    private DecisionReleaseTestFixture fixture() {
        return new DecisionReleaseTestFixture(port, decisionTestMapper, jdbc, transactionManager, identifiers);
    }
}
