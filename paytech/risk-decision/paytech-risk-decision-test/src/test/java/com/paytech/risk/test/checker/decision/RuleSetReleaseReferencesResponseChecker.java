package com.paytech.risk.test.checker.decision;

import static com.paytech.risk.decision.api.enums.RiskDecisionConfigurationErrorCode.RISK_DECISION_CONFIGURATION_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;
import com.paytech.risk.test.builder.decision.RuleSetBuilder;
import com.paytech.risk.test.mocker.decision.RuleSetMocker.MockedRelease;
import com.paytech.risk.test.mocker.decision.RuleSetMocker.MockedRuleSet;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 规则集发布引用的 HTTP 响应 Checker
 * <li>全根 inUse 与筛选分页分开断言；引用逐字段核对插入的发布事实，可空时间须为 JSON null。</li>
 *
 * @author Kenny
 */
public final class RuleSetReleaseReferencesResponseChecker
    extends AbstractHttpResponseChecker<RuleSetReleaseReferencesResponseChecker> {

    public RuleSetReleaseReferencesResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper,
        String requestId) {
        super(result, mapper, requestId);
    }

    @Override
    protected RuleSetReleaseReferencesResponseChecker self() {
        return this;
    }

    /** 成功响应、规则集 ID 与全根使用标志。 */
    public RuleSetReleaseReferencesResponseChecker assertUsage(MockedRuleSet ruleSet, boolean inUse) {
        assertStatus(200);
        assertAll(() -> assertEquals(Long.toString(ruleSet.ruleSetId()), body().path("ruleSetId").asString()),
            () -> assertEquals(inUse, body().path("inUse").asBoolean(), "inUse"));
        return this;
    }

    /** 引用分页元数据。 */
    public RuleSetReleaseReferencesResponseChecker assertPage(int page, int size, long total) {
        JsonNode references = body().path("references");
        assertAll(() -> assertEquals(page, references.path("page").asInt(), "page"),
            () -> assertEquals(size, references.path("size").asInt(), "size"),
            () -> assertEquals(total, references.path("total").asLong(), "total"));
        return this;
    }

    /** 当前页按给定顺序返回这些发布引用。 */
    public RuleSetReleaseReferencesResponseChecker assertReferences(MockedRelease... expected) {
        JsonNode items = body().path("references").path("items");
        assertEquals(expected.length, items.size(), "发布引用数量不符");
        for (int i = 0; i < expected.length; i++) {
            MockedRelease release = expected[i];
            JsonNode actual = items.get(i);
            assertAll("发布引用 " + release.releaseNo(),
                () -> assertEquals(Long.toString(release.releaseId()), actual.path("releaseId").asString()),
                () -> assertEquals(release.releaseNo(), actual.path("releaseNo").asString()),
                () -> assertEquals(release.environment().getCode(), actual.path("environmentCode").asString()),
                () -> assertEquals(release.status().getCode(), actual.path("status").asString()),
                () -> assertEquals(RuleSetBuilder.POINT_CODE, actual.path("pointCode").asString()),
                () -> assertEquals(Long.toString(release.pointVersionId()), actual.path("pointVersionId").asString()),
                () -> assertEquals(release.pointVersionNo(), actual.path("pointVersionNo").asInt()),
                () -> assertEquals(Long.toString(release.ruleSetVersionId()),
                    actual.path("ruleSetVersionId").asString()),
                () -> assertEquals(release.versionNo(), actual.path("versionNo").asInt()),
                () -> assertTime(release.activatedTime() == null ? null
                    : RuleSetHistoryResponseChecker.time(release.activatedTime()), actual.path("activatedTime")),
                () -> assertTime(release.supersededTime() == null ? null
                    : RuleSetHistoryResponseChecker.time(release.supersededTime()), actual.path("supersededTime")));
        }
        return this;
    }

    /** 规则集或精确版本不存在、或版本不属于该规则集。 */
    public RuleSetReleaseReferencesResponseChecker assertNotFound() {
        return assertProblem(404, RISK_DECISION_CONFIGURATION_NOT_FOUND.getCode());
    }

    private static void assertTime(String expected, JsonNode actual) {
        if (expected == null) {
            assertTrue(actual.isNull(), "时间应为 null");
        } else {
            assertEquals(expected, actual.asString());
        }
    }
}
