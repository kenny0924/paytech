package com.paytech.risk.auto.ext.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.paytech.risk.api.decision.request.CreateTestReleaseRequestBody;
import com.paytech.risk.api.decision.response.TestReleaseDetailResponseBody;
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

    /** 创建 Candidate 与 Validate 缺少必填键时在入口拒绝，不进入业务处理。 */
    @Test
    void test_missingRequiredKeysAreRejected() throws Exception {
        try (RiskDecisionHttpClient http = new RiskDecisionHttpClient(port)) {
            for (String body : java.util.List.of("{}", "{\"ruleSetVersionId\":\"1\"}")) {
                RiskDecisionHttpClient.assertProblem(http.configuration("POST", "/decision-releases/v1/candidates",
                    DecisionReleaseTestFixture.ACTOR, DecisionReleaseTestFixture.SESSION, body), 400, "INVALID_REQUEST");
            }
            RiskDecisionHttpClient.assertProblem(http.configuration("POST", "/decision-releases/v1/candidates/1/validate",
                DecisionReleaseTestFixture.ACTOR, DecisionReleaseTestFixture.SESSION, "{}"), 400, "INVALID_REQUEST");
        }
    }

    @Test
    void test_createReplaysStableCandidateAndPublishesListAndDetail() throws Exception {
        try (DecisionReleaseTestFixture fixture = fixture()) {
            var candidate = fixture.arrangeFrozenCandidate();
            var created = candidate.created();

            assertTrue(created.getReleaseNo() > 0);
            assertEquals("CHECKOUT_PREPARE", created.getPointCode());
            assertEquals("TEST", created.getEnvironmentCode());
            assertEquals("DRAFT", created.getStatus());
            assertEquals(1, created.getVersion());
            assertEquals(64, created.getContentHash().length());
            assertEquals(DecisionReleaseTestFixture.ACTOR, created.getCreatedBy());
            assertNull(created.getInitialStateChange().getFromStatus());
            assertEquals("DRAFT", created.getInitialStateChange().getToStatus());
            assertEquals("CANDIDATE_CREATED", created.getInitialStateChange().getReasonCode());
            assertEquals("OPERATIONS_COMMAND", created.getInitialStateChange().getTriggerType());
            assertEquals(1, created.getInitialStateChange().getAggregateVersion());

            ReleaseFacts stored = fixture.releaseFacts(candidate);
            assertEquals(new ReleaseFacts("DRAFT", 1, created.getContentHash(), 1, 0), stored);
            assertEquals(1, fixture.releaseCount());
            HttpResponse<String> detailResponse = fixture.getCandidate(candidate);
            assertEquals(200, detailResponse.statusCode(), detailResponse.body());
            TestReleaseDetailResponseBody detail = fixture.body(
                detailResponse, TestReleaseDetailResponseBody.class);
            assertEquals(created.getReleaseId(), detail.getReleaseId());
            assertEquals(created.getPointVersionId(), detail.getFrozenComposition().getPointVersionId());
            assertEquals(created.getRuleSetVersionId(), detail.getFrozenComposition().getRuleSetVersionId());
            assertNull(detail.getAllStaticChecksPassed());
            assertFalse(detail.getWaitingForShadow());
            assertEquals(1, detail.getStateChanges().size());
            assertTrue(detail.getStaticChecks().isEmpty());

            HttpResponse<String> listResponse = fixture.listDraftCandidates();
            assertEquals(200, listResponse.statusCode(), listResponse.body());
            var json = fixture.json(listResponse);
            assertEquals(1, json.get("page").intValue());
            assertEquals(10, json.get("size").intValue());
            assertTrue(json.get("total").longValue() >= 1);
            var item = StreamSupport.stream(json.get("items").spliterator(), false)
                .filter(value -> created.getReleaseId().equals(value.get("releaseId").textValue()))
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
                candidate.request().getRuleSetVersionId(), "0".repeat(64));

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
