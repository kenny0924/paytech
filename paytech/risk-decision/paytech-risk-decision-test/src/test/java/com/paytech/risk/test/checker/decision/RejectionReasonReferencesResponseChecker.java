package com.paytech.risk.test.checker.decision;

import static com.paytech.risk.api.enums.RiskDecisionConfigurationErrorCode.RISK_DICTIONARY_NOT_FOUND;
import static com.paytech.risk.test.checker.decision.DictionaryEntryResponseChecker.assertText;
import static com.paytech.risk.test.checker.decision.DictionaryEntryResponseChecker.fields;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.function.Executable;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;
import com.paytech.risk.decision.domain.enums.DecisionEnvironment;
import com.paytech.risk.test.builder.decision.RuleSetBuilder;
import com.paytech.risk.test.mocker.decision.DictionaryReferenceMocker.MockedFlowEnd;
import com.paytech.risk.test.mocker.decision.DictionaryReferenceMocker.MockedRule;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 拒绝原因引用分页的 HTTP 响应 Checker
 * <li>规则与流程两种来源统一分页；合同排序为保存版本 ID 倒序、来源类型（FLOW_END 先于 RULE_SET）、来源行 ID 升序。</li>
 * <li>两类来源的专属字段互斥，另一来源的字段必须为 JSON null；流程来源的活动环境为空列表。</li>
 *
 * @author Kenny
 */
public final class RejectionReasonReferencesResponseChecker
    extends AbstractHttpResponseChecker<RejectionReasonReferencesResponseChecker> {

    static final Set<String> FIELDS = Set.of("ruleSetId", "pointCode", "ruleSetCode", "ruleSetName",
        "ruleSetVersionId", "versionNo", "ruleCode", "ruleName", "referenceKind", "actionSequenceNo",
        "targetBindingCode", "ruleSetStatus", "isCurrentConfiguration", "activeEnvironments", "sourceType", "flowId",
        "flowCode", "flowName", "flowVersionId", "flowVersionNo", "nodeKey", "nodeName", "flowStatus");

    static final List<String> RULE_FIELDS = List.of("ruleSetId", "ruleSetCode", "ruleSetName", "ruleSetVersionId",
        "versionNo", "ruleCode", "ruleName", "ruleSetStatus");

    static final List<String> FLOW_FIELDS = List.of("flowId", "flowCode", "flowName", "flowVersionId",
        "flowVersionNo", "nodeKey", "nodeName", "flowStatus");

    public RejectionReasonReferencesResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper,
        String requestId) {
        super(result, mapper, requestId);
    }

    @Override
    protected RejectionReasonReferencesResponseChecker self() {
        return this;
    }

    /** 一条期望引用及其合同排序键。 */
    public sealed interface Expected permits RuleReference, FlowReference {
        long sourceVersionId();

        String sourceType();

        long sourceRowId();
    }

    /** 规则来源：规则所在精确版本及该版本的活动环境（按编码排序）。 */
    public record RuleReference(MockedRule rule, List<DecisionEnvironment> activeEnvironments) implements Expected {
        @Override
        public long sourceVersionId() {
            return rule.version().ruleSetVersionId();
        }

        @Override
        public String sourceType() {
            return "RULE_SET";
        }

        @Override
        public long sourceRowId() {
            return rule.ruleId();
        }
    }

    /** 流程来源：DENY 结束节点所在精确流程版本。 */
    public record FlowReference(MockedFlowEnd end) implements Expected {
        @Override
        public long sourceVersionId() {
            return end.version().flowVersionId();
        }

        @Override
        public String sourceType() {
            return "FLOW_END";
        }

        @Override
        public long sourceRowId() {
            return end.nodeId();
        }
    }

    /** 期望的规则来源引用。 */
    public static Expected rule(MockedRule rule, DecisionEnvironment... activeEnvironments) {
        return new RuleReference(rule, List.of(activeEnvironments));
    }

    /** 期望的流程结束节点引用。 */
    public static Expected flow(MockedFlowEnd end) {
        return new FlowReference(end);
    }

    /** 成功分页：total 为全部期望条数，当前页为按合同排序后的对应切片，逐项核对全部字段。 */
    public RejectionReasonReferencesResponseChecker assertReferences(int page, int size, Expected... all) {
        assertStatus(200);
        JsonNode body = body();
        List<Expected> sorted = Arrays.stream(all).sorted(Comparator.comparingLong(Expected::sourceVersionId)
            .reversed().thenComparing(Expected::sourceType).thenComparingLong(Expected::sourceRowId)).toList();
        List<Expected> expected = sorted.subList(Math.min(sorted.size(), (page - 1) * size),
            Math.min(sorted.size(), page * size));
        JsonNode items = body.path("items");
        assertAll(() -> assertEquals(page, body.path("page").asInt(), "page"),
            () -> assertEquals(size, body.path("size").asInt(), "size"),
            () -> assertEquals(all.length, body.path("total").asLong(), "total"),
            () -> assertEquals(expected.size(), items.size(), "当前页条数"));
        for (int i = 0; i < expected.size(); i++) {
            JsonNode actual = items.get(i);
            switch (expected.get(i)) {
                case RuleReference rule -> assertRule(rule, actual);
                case FlowReference flow -> assertFlow(flow.end(), actual);
            }
        }
        return this;
    }

    /** 原因编码未登记。 */
    public RejectionReasonReferencesResponseChecker assertNotFound() {
        return assertProblem(404, RISK_DICTIONARY_NOT_FOUND.getCode());
    }

    private static void assertRule(RuleReference expected, JsonNode actual) {
        MockedRule rule = expected.rule();
        assertAll("规则引用 " + rule.ruleCode() + "@" + rule.version().ruleSetVersionId(),
            () -> assertEquals(FIELDS, fields(actual), "字段集合"),
            () -> assertEquals("RULE_SET", actual.path("sourceType").asString()),
            () -> assertEquals("REJECTION_REASON", actual.path("referenceKind").asString()),
            () -> assertEquals(RuleSetBuilder.POINT_CODE, actual.path("pointCode").asString()),
            () -> assertEquals(Long.toString(rule.ruleSet().ruleSet().ruleSetId()), actual.path("ruleSetId").asString()),
            () -> assertEquals(rule.ruleSet().ruleSet().ruleSetCode(), actual.path("ruleSetCode").asString()),
            () -> assertEquals(rule.version().spec().ruleSetName(), actual.path("ruleSetName").asString()),
            () -> assertEquals(Long.toString(rule.version().ruleSetVersionId()),
                actual.path("ruleSetVersionId").asString()),
            () -> assertEquals(rule.version().versionNo(), actual.path("versionNo").asInt()),
            () -> assertEquals(rule.ruleCode(), actual.path("ruleCode").asString()),
            () -> assertEquals(rule.ruleName(), actual.path("ruleName").asString()),
            () -> assertEquals(rule.ruleSet().status().getCode(), actual.path("ruleSetStatus").asString()),
            () -> assertEquals(rule.isCurrentConfiguration(), actual.path("isCurrentConfiguration").asBoolean()),
            () -> assertEquals(expected.activeEnvironments().stream().map(DecisionEnvironment::getCode).toList(),
                actual.path("activeEnvironments").valueStream().map(JsonNode::asString).toList(), "活动环境"),
            () -> assertNulls(actual, "actionSequenceNo", "targetBindingCode"),
            () -> assertNulls(actual, FLOW_FIELDS.toArray(String[]::new)));
    }

    private static void assertFlow(MockedFlowEnd end, JsonNode actual) {
        assertAll("流程引用 " + end.nodeKey(), () -> assertEquals(FIELDS, fields(actual), "字段集合"),
            () -> assertEquals("FLOW_END", actual.path("sourceType").asString()),
            () -> assertEquals("REJECTION_REASON", actual.path("referenceKind").asString()),
            () -> assertEquals(RuleSetBuilder.POINT_CODE, actual.path("pointCode").asString()),
            () -> assertEquals(Long.toString(end.flow().flowId()), actual.path("flowId").asString()),
            () -> assertEquals(end.flow().flowCode(), actual.path("flowCode").asString()),
            () -> assertEquals(end.version().flowName(), actual.path("flowName").asString()),
            () -> assertEquals(Long.toString(end.version().flowVersionId()), actual.path("flowVersionId").asString()),
            () -> assertEquals(end.version().versionNo(), actual.path("flowVersionNo").asInt()),
            () -> assertEquals(end.nodeKey(), actual.path("nodeKey").asString()),
            () -> assertEquals(end.nodeName(), actual.path("nodeName").asString()),
            () -> assertEquals(end.flow().status().getCode(), actual.path("flowStatus").asString()),
            () -> assertEquals(end.isCurrentConfiguration(), actual.path("isCurrentConfiguration").asBoolean()),
            () -> assertTrue(actual.path("activeEnvironments").isArray()
                && actual.path("activeEnvironments").isEmpty(), "流程来源活动环境应为空列表"),
            () -> assertNulls(actual, "actionSequenceNo", "targetBindingCode"),
            () -> assertNulls(actual, RULE_FIELDS.toArray(String[]::new)));
    }

    private static void assertNulls(JsonNode actual, String... names) {
        assertAll(Arrays.stream(names).map(name -> (Executable)() -> assertText(null, actual.path(name))));
    }
}
