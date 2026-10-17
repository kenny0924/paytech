package com.paytech.risk.test.checker.decision;

import static com.paytech.risk.api.enums.RiskDecisionConfigurationErrorCode.RISK_DECISION_CONFIGURATION_NOT_FOUND;
import static com.paytech.risk.test.checker.decision.RuleSetHistoryResponseChecker.time;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;
import com.paytech.risk.decision.domain.enums.DecisionEnvironment;
import com.paytech.risk.test.builder.decision.RuleSetBuilder;
import com.paytech.risk.test.mapper.decision.ReleaseTestMapper.AuditRow;
import com.paytech.risk.test.mocker.decision.CandidateMocker.MockedCandidate;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * TEST Candidate 详情的 HTTP 响应 Checker
 * <li>根、冻结组成、状态审计与静态检查逐字段核对插入的事实；可空字段须为 JSON null。</li>
 * <li>三态检查结果与等待 Shadow 标志由用例按场景给出，不从响应推导。</li>
 *
 * @author Kenny
 */
public final class TestReleaseDetailResponseChecker extends AbstractHttpResponseChecker<TestReleaseDetailResponseChecker> {

    public TestReleaseDetailResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper,
        String requestId) {
        super(result, mapper, requestId);
    }

    @Override
    protected TestReleaseDetailResponseChecker self() {
        return this;
    }

    /** 成功响应的根字段、冻结组成和派生标志。 */
    public TestReleaseDetailResponseChecker assertCandidate(MockedCandidate expected, Boolean allStaticChecksPassed,
        boolean waitingForShadow) {
        assertStatus(200);
        JsonNode body = body();
        var composition = expected.composition();
        JsonNode frozen = body.path("frozenComposition");
        assertAll("Candidate " + expected.releaseId(),
            () -> assertEquals(Long.toString(expected.releaseId()), body.path("releaseId").asString()),
            () -> assertEquals(expected.releaseNo(), body.path("releaseNo").asInt()),
            () -> assertEquals(Long.toString(RuleSetBuilder.DECISION_POINT_ID), body.path("decisionPointId").asString()),
            () -> assertEquals(RuleSetBuilder.POINT_CODE, body.path("pointCode").asString()),
            () -> assertEquals(Long.toString(composition.pointVersionId()), body.path("pointVersionId").asString()),
            () -> assertEquals(Long.toString(composition.ruleSetVersionId()), body.path("ruleSetVersionId").asString()),
            () -> assertEquals(DecisionEnvironment.TEST.getCode(), body.path("environmentCode").asString()),
            () -> assertEquals(expected.status().getCode(), body.path("status").asString()),
            () -> assertEquals(expected.version(), body.path("version").asInt()),
            () -> assertEquals(expected.contentHash(), body.path("contentHash").asString()),
            () -> assertEquals(Long.toString(expected.createdBy()), body.path("createdBy").asString()),
            () -> assertEquals(time(expected.createTime()), body.path("createTime").asString()),
            () -> assertBoolean(allStaticChecksPassed, body.path("allStaticChecksPassed")),
            () -> assertEquals(waitingForShadow, body.path("waitingForShadow").asBoolean()),
            () -> assertTrue(body.path("waitingForShadow").isBoolean(), "waitingForShadow 必须为布尔值"),
            () -> assertEquals(Long.toString(RuleSetBuilder.DECISION_POINT_ID), frozen.path("decisionPointId").asString()),
            () -> assertEquals(RuleSetBuilder.POINT_CODE, frozen.path("pointCode").asString()),
            () -> assertEquals(Long.toString(composition.pointVersionId()), frozen.path("pointVersionId").asString()),
            () -> assertEquals(composition.pointVersionNo(), frozen.path("pointVersionNo").asInt()),
            () -> assertEquals(composition.pointHash(), frozen.path("pointVersionContentHash").asString()),
            () -> assertEquals(Long.toString(composition.ruleSetVersionId()), frozen.path("ruleSetVersionId").asString()),
            () -> assertEquals(composition.ruleSetCode(), frozen.path("ruleSetCode").asString()),
            () -> assertEquals(composition.versionNo(), frozen.path("ruleSetVersionNo").asInt()),
            () -> assertEquals(composition.ruleSetHash(), frozen.path("ruleSetContentHash").asString()),
            () -> assertEquals(9, frozen.size(), "冻结组成字段数量"),
            () -> assertEquals(17, body.size(), "详情字段数量"));
        return assertStateChanges(expected.stateChanges()).assertStaticChecks(expected.staticChecks());
    }

    /** 状态审计按聚合版本顺序逐字段相等。 */
    private TestReleaseDetailResponseChecker assertStateChanges(List<AuditRow> expected) {
        JsonNode items = body().path("stateChanges");
        assertEquals(expected.size(), items.size(), "状态审计数量不符");
        for (int i = 0; i < expected.size(); i++) {
            AuditRow row = expected.get(i);
            JsonNode actual = items.get(i);
            assertAll("状态审计 v" + row.aggregateVersion(),
                () -> assertEquals(Long.toString(row.auditEventId()), actual.path("auditEventId").asString()),
                () -> assertEquals(time(row.eventTime()), actual.path("eventTime").asString()),
                () -> assertCode(row.fromStatus() == null ? null : row.fromStatus().getCode(), actual.path("fromStatus")),
                () -> assertEquals(row.toStatus().getCode(), actual.path("toStatus").asString()),
                () -> assertEquals(row.reasonCode(), actual.path("reasonCode").asString()),
                () -> assertEquals(row.triggerType(), actual.path("triggerType").asString()),
                () -> assertEquals(row.triggerId(), actual.path("triggerId").asString()),
                () -> assertEquals(Long.toString(row.actorId()), actual.path("actorId").asString()),
                () -> assertEquals(row.aggregateVersion(), actual.path("aggregateVersion").asInt()),
                () -> assertEquals(9, actual.size(), "状态审计字段数量"));
        }
        return this;
    }

    /** 静态检查按固定八项顺序逐字段相等；未完成为空数组。 */
    private TestReleaseDetailResponseChecker assertStaticChecks(List<AuditRow> expected) {
        JsonNode items = body().path("staticChecks");
        assertTrue(items.isArray(), "staticChecks 必须为数组");
        assertEquals(expected.size(), items.size(), "静态检查数量不符");
        for (int i = 0; i < expected.size(); i++) {
            AuditRow row = expected.get(i);
            JsonNode actual = items.get(i);
            assertAll("静态检查 " + row.checkCode(),
                () -> assertEquals(Long.toString(row.auditEventId()), actual.path("auditEventId").asString()),
                () -> assertEquals(time(row.eventTime()), actual.path("eventTime").asString()),
                () -> assertEquals(row.checkCode().getCode(), actual.path("checkCode").asString()),
                () -> assertEquals(row.checkStatus().getCode(), actual.path("checkStatus").asString()),
                () -> assertEquals(row.severity().getCode(), actual.path("severity").asString()),
                () -> assertCode(row.errorCode(), actual.path("errorCode")),
                () -> assertCode(row.errorSummary(), actual.path("errorSummary")),
                () -> assertEquals(7, actual.size(), "静态检查字段数量"));
        }
        return this;
    }

    /** 发布不存在。 */
    public TestReleaseDetailResponseChecker assertNotFound() {
        return assertProblem(404, RISK_DECISION_CONFIGURATION_NOT_FOUND.getCode());
    }

    static void assertCode(String expected, JsonNode actual) {
        if (expected == null) {
            assertTrue(actual.isNull(), "应为 JSON null，实际：" + actual);
        } else {
            assertEquals(expected, actual.asString());
        }
    }

    static void assertBoolean(Boolean expected, JsonNode actual) {
        if (expected == null) {
            assertTrue(actual.isNull(), "应为 JSON null，实际：" + actual);
        } else {
            assertTrue(actual.isBoolean(), "应为布尔值，实际：" + actual);
            assertEquals(expected, actual.asBoolean());
        }
    }
}
