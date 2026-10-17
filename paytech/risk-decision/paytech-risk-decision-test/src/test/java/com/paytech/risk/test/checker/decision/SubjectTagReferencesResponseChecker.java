package com.paytech.risk.test.checker.decision;

import static com.paytech.risk.decision.api.enums.RiskDecisionConfigurationErrorCode.RISK_DICTIONARY_NOT_FOUND;
import static com.paytech.risk.test.checker.decision.DictionaryEntryResponseChecker.fields;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;
import com.paytech.risk.decision.domain.enums.DecisionEnvironment;
import com.paytech.risk.test.builder.decision.RuleSetBuilder;
import com.paytech.risk.test.mocker.decision.DictionaryReferenceMocker.MockedRule;
import com.paytech.risk.test.mocker.decision.DictionaryReferenceMocker.MockedRuleAction;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 主体标签引用分页的 HTTP 响应 Checker
 * <li>每个保存了该标签的 ADD_RISK_TAG 动作是一条引用；合同排序为规则集版本 ID 倒序、规则 ID 升序、动作序号升序。</li>
 *
 * @author Kenny
 */
public final class SubjectTagReferencesResponseChecker
    extends AbstractHttpResponseChecker<SubjectTagReferencesResponseChecker> {

    static final Set<String> FIELDS = Set.of("ruleSetId", "pointCode", "ruleSetCode", "ruleSetName",
        "ruleSetVersionId", "versionNo", "ruleCode", "ruleName", "referenceKind", "actionSequenceNo",
        "targetBindingCode", "ruleSetStatus", "isCurrentConfiguration", "activeEnvironments");

    public SubjectTagReferencesResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper,
        String requestId) {
        super(result, mapper, requestId);
    }

    @Override
    protected SubjectTagReferencesResponseChecker self() {
        return this;
    }

    /** 一条期望的标签动作引用及该版本的活动环境（按编码排序）。 */
    public record Expected(MockedRuleAction action, List<DecisionEnvironment> activeEnvironments) {
    }

    /** 期望的标签动作引用。 */
    public static Expected tag(MockedRuleAction action, DecisionEnvironment... activeEnvironments) {
        return new Expected(action, List.of(activeEnvironments));
    }

    /** 成功分页：total 为全部期望条数，当前页为按合同排序后的对应切片，逐项核对全部字段。 */
    public SubjectTagReferencesResponseChecker assertReferences(int page, int size, Expected... all) {
        assertStatus(200);
        JsonNode body = body();
        List<Expected> sorted = Arrays.stream(all)
            .sorted(Comparator
                .comparingLong((Expected e) -> e.action().rule().version().ruleSetVersionId()).reversed()
                .thenComparingLong(e -> e.action().rule().ruleId())
                .thenComparingInt(e -> e.action().sequenceNo()))
            .toList();
        List<Expected> expected = sorted.subList(Math.min(sorted.size(), (page - 1) * size),
            Math.min(sorted.size(), page * size));
        JsonNode items = body.path("items");
        assertAll(() -> assertEquals(page, body.path("page").asInt(), "page"),
            () -> assertEquals(size, body.path("size").asInt(), "size"),
            () -> assertEquals(all.length, body.path("total").asLong(), "total"),
            () -> assertEquals(expected.size(), items.size(), "当前页条数"));
        for (int i = 0; i < expected.size(); i++) {
            assertReference(expected.get(i), items.get(i));
        }
        return this;
    }

    /** 标签编码未登记。 */
    public SubjectTagReferencesResponseChecker assertNotFound() {
        return assertProblem(404, RISK_DICTIONARY_NOT_FOUND.getCode());
    }

    private static void assertReference(Expected expected, JsonNode actual) {
        MockedRuleAction action = expected.action();
        MockedRule rule = action.rule();
        assertAll("标签引用 " + rule.ruleCode() + "#" + action.sequenceNo(),
            () -> assertEquals(FIELDS, fields(actual), "字段集合"),
            () -> assertEquals("SUBJECT_TAG", actual.path("referenceKind").asString()),
            () -> assertEquals(RuleSetBuilder.POINT_CODE, actual.path("pointCode").asString()),
            () -> assertEquals(Long.toString(rule.ruleSet().ruleSet().ruleSetId()), actual.path("ruleSetId").asString()),
            () -> assertEquals(rule.ruleSet().ruleSet().ruleSetCode(), actual.path("ruleSetCode").asString()),
            () -> assertEquals(rule.version().spec().ruleSetName(), actual.path("ruleSetName").asString()),
            () -> assertEquals(Long.toString(rule.version().ruleSetVersionId()),
                actual.path("ruleSetVersionId").asString()),
            () -> assertEquals(rule.version().versionNo(), actual.path("versionNo").asInt()),
            () -> assertEquals(rule.ruleCode(), actual.path("ruleCode").asString()),
            () -> assertEquals(rule.ruleName(), actual.path("ruleName").asString()),
            () -> assertEquals(action.sequenceNo(), actual.path("actionSequenceNo").asInt()),
            () -> assertEquals(action.targetBindingCode(), actual.path("targetBindingCode").asString()),
            () -> assertEquals(rule.ruleSet().status().getCode(), actual.path("ruleSetStatus").asString()),
            () -> assertEquals(rule.isCurrentConfiguration(), actual.path("isCurrentConfiguration").asBoolean()),
            () -> assertEquals(expected.activeEnvironments().stream().map(DecisionEnvironment::getCode).toList(),
                actual.path("activeEnvironments").valueStream().map(JsonNode::asString).toList(), "活动环境"));
    }
}
