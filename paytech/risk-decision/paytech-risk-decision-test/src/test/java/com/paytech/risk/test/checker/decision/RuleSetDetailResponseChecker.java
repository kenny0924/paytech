package com.paytech.risk.test.checker.decision;

import static com.paytech.risk.decision.api.enums.RiskDecisionConfigurationErrorCode.RISK_DECISION_CONFIGURATION_NOT_FOUND;
import static com.paytech.risk.test.checker.decision.RuleSetHistoryResponseChecker.time;
import static com.paytech.risk.test.checker.decision.RuleSetListResponseChecker.text;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;
import com.paytech.risk.decision.domain.enums.ComparisonOperator;
import com.paytech.risk.decision.domain.enums.DecisionValueType;
import com.paytech.risk.decision.domain.enums.NodeType;
import com.paytech.risk.decision.domain.enums.OperandRole;
import com.paytech.risk.decision.domain.enums.OperandSource;
import com.paytech.risk.decision.domain.enums.RuleSetVersionStatus;
import com.paytech.risk.decision.domain.enums.VariableUsage;
import com.paytech.risk.test.builder.decision.RuleSetBuilder;
import com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder;
import com.paytech.risk.test.mocker.decision.RuleSetMocker.MockedRuleSet;
import com.paytech.risk.test.mocker.decision.RuleSetMocker.MockedVersion;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 规则集准确版本详情的 HTTP 响应 Checker
 * <li>版本头逐字段核对插入事实；规则按 Mocker 写入的唯一规则还原，节点键为节点 ID 的十进制文本。</li>
 * <li>旧格式不引用变量，当前变量为空；统一变量格式附带被引用变量的当前展示值，读取时间落在请求窗口内。</li>
 *
 * @author Kenny
 */
public final class RuleSetDetailResponseChecker extends AbstractHttpResponseChecker<RuleSetDetailResponseChecker> {

    private final Instant requestedAfter;

    private final Instant respondedBefore;

    /** 记录请求前后时刻，用于核对当前变量读取时间。 */
    public RuleSetDetailResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper, String requestId,
        Instant requestedAfter, Instant respondedBefore) {
        super(result, mapper, requestId);
        this.requestedAfter = requestedAfter;
        this.respondedBefore = respondedBefore;
    }

    @Override
    protected RuleSetDetailResponseChecker self() {
        return this;
    }

    /** 旧格式最小规则版本的完整详情。 */
    public RuleSetDetailResponseChecker assertLegacyVersion(MockedRuleSet ruleSet, int versionNo,
        Long pointVersionId) {
        MockedVersion version = ruleSet.version(versionNo);
        assertHeader(ruleSet, version, pointVersionId, "LEGACY");
        JsonNode body = body();
        long nodeId = version.ruleId() + 1;
        assertAll("旧格式规则与当前变量",
            () -> assertRule(body.path("rules"), ComparisonOperator.IS_NOT_NULL, nodeId,
                List.of(operand(OperandRole.LEFT, OperandSource.CONTEXT, "country_code", null, null))),
            () -> assertEquals(0, body.path("currentVariables").size(), "旧格式不引用变量"),
            () -> assertTrue(body.path("currentVariables").isArray(), "currentVariables 为数组"),
            () -> assertValuesAsOf(body),
            () -> assertTrue(body.path("variableIssues").isArray() && body.path("variableIssues").isEmpty(),
                "variableIssues 为空数组"));
        return this;
    }

    /** 统一变量规则版本的完整详情，附带 INPUT_COUNTRY_CODE 的当前定义行投影。 */
    public RuleSetDetailResponseChecker assertVariableVersion(MockedRuleSet ruleSet, int versionNo,
        Map<String, Object> countryVariable) {
        MockedVersion version = ruleSet.version(versionNo);
        assertHeader(ruleSet, version, null, RuleSetCreateRequestBuilder.VARIABLE_REFERENCE);
        JsonNode body = body();
        long nodeId = version.ruleId() + 1;
        assertAll("统一变量规则与当前变量",
            () -> assertRule(body.path("rules"), ComparisonOperator.EQ, nodeId,
                List.of(operand(OperandRole.LEFT, OperandSource.VARIABLE, null, RuleSetBuilder.COUNTRY_VARIABLE, null),
                    operand(OperandRole.RIGHT, OperandSource.LITERAL, null, null, "AE"))),
            () -> assertEquals(1, body.path("currentVariables").size(), "只附带被引用的变量"),
            () -> assertVariable(countryVariable, body.path("currentVariables").path(0)),
            () -> assertValuesAsOf(body),
            () -> assertTrue(body.path("variableIssues").isArray() && body.path("variableIssues").isEmpty(),
                "variableIssues 为空数组"));
        return this;
    }

    /** 规则集不存在或已整体删除。 */
    public RuleSetDetailResponseChecker assertNotFound() {
        return assertProblem(404, RISK_DECISION_CONFIGURATION_NOT_FOUND.getCode());
    }

    private void assertHeader(MockedRuleSet ruleSet, MockedVersion version, Long pointVersionId,
        String operandFormat) {
        assertStatus(200);
        JsonNode body = body();
        boolean frozen = version.spec().status() == RuleSetVersionStatus.FROZEN;
        assertAll("详情版本头",
            () -> assertEquals(18, body.size(), "字段数量"),
            () -> assertEquals(Long.toString(version.ruleSetVersionId()), body.path("ruleSetVersionId").asString()),
            () -> assertEquals(pointVersionId == null ? null : pointVersionId.toString(),
                text(body, "pointVersionId")),
            () -> assertEquals(RuleSetBuilder.POINT_CODE, body.path("pointCode").asString()),
            () -> assertEquals(ruleSet.ruleSetCode(), body.path("ruleSetCode").asString()),
            () -> assertEquals(version.spec().ruleSetName(), body.path("ruleSetName").asString()),
            () -> assertEquals(version.versionNo(), body.path("versionNo").asInt()),
            () -> assertEquals(version.spec().status().getCode(), body.path("status").asString()),
            () -> assertEquals(version.contentHash(), body.path("contentHash").asString()),
            () -> assertEquals(Long.toString(version.spec().createdBy()), body.path("createdBy").asString()),
            () -> assertEquals(version.spec().changeReason(), body.path("changeReason").asString()),
            () -> assertEquals(time(version.spec().createTime()), body.path("createTime").asString()),
            () -> assertEquals(frozen ? Long.toString(version.spec().createdBy()) : null, text(body, "frozenBy")),
            () -> assertEquals(frozen ? time(version.spec().createTime()) : null, text(body, "frozenTime")),
            () -> assertEquals(operandFormat, body.path("operandFormat").asString()));
    }

    /** 唯一规则：MOCK_RULE、优先级 10、启用、ALLOW_CHECKOUT v1、无附加动作；根节点为单个谓词。 */
    private static void assertRule(JsonNode rules, ComparisonOperator operator, long nodeId,
        List<Map<String, Object>> operands) {
        assertEquals(1, rules.size(), "规则数量");
        JsonNode rule = rules.get(0);
        JsonNode nodes = rule.path("conditionNodes");
        assertAll("规则 MOCK_RULE",
            () -> assertEquals(9, rule.size(), "规则字段数量"),
            () -> assertEquals("MOCK_RULE", rule.path("ruleCode").asString()),
            () -> assertEquals("Mock rule", rule.path("ruleName").asString()),
            () -> assertEquals(10, rule.path("priorityNo").asInt()),
            () -> assertTrue(rule.path("enabledFlag").asBoolean()),
            () -> assertEquals(null, text(rule, "reasonCode")),
            () -> assertEquals(null, text(rule, "reasonMessage")),
            () -> assertEquals(RuleSetCreateRequestBuilder.ALLOW, rule.path("action").path("actionCode").asString()),
            () -> assertEquals(1, rule.path("action").path("actionVersionNo").asInt()),
            () -> assertTrue(rule.path("additionalActions").isArray() && rule.path("additionalActions").isEmpty(),
                "无附加动作"),
            () -> assertEquals(1, nodes.size(), "节点数量"));
        JsonNode node = nodes.get(0);
        assertAll("根谓词节点",
            () -> assertEquals(Long.toString(nodeId), node.path("nodeKey").asString()),
            () -> assertEquals(null, text(node, "parentNodeKey")),
            () -> assertEquals(1, node.path("sequenceNo").asInt()),
            () -> assertEquals(NodeType.PREDICATE.getCode(), node.path("nodeType").asString()),
            () -> assertEquals(null, text(node, "groupOperator")),
            () -> assertEquals(operator.getCode(), node.path("comparisonOperator").asString()),
            () -> assertEquals(operands.size(), node.path("operands").size(), "操作数数量"));
        for (int i = 0; i < operands.size(); i++) {
            JsonNode actual = node.path("operands").get(i);
            Map<String, Object> expected = operands.get(i);
            assertAll("操作数 " + i, () -> assertEquals(10, actual.size(), "操作数字段数量"),
                () -> assertEquals(expected.get("operandRole"), actual.path("operandRole").asString()),
                () -> assertEquals(1, actual.path("valueSequence").asInt()),
                () -> assertEquals(expected.get("sourceType"), actual.path("sourceType").asString()),
                () -> assertEquals(expected.get("fieldCode"), text(actual, "fieldCode")),
                () -> assertEquals(DecisionValueType.STRING.getCode(), actual.path("valueType").asString()),
                () -> assertEquals(expected.get("literalValue"), text(actual, "literalValue")),
                () -> assertEquals(expected.get("variableCode"), text(actual, "variableCode")),
                () -> assertEquals(null, text(actual, "numericUnit")),
                () -> assertEquals(null, text(actual, "currencyCode")),
                () -> assertEquals(null, text(actual, "currencyVariableCode")));
        }
    }

    /** 当前变量展示投影逐字段对应变量定义行。 */
    private static void assertVariable(Map<String, Object> row, JsonNode actual) {
        assertAll("当前变量 " + row.get("variable_code"),
            () -> assertEquals(18, actual.size(), "变量字段数量"),
            () -> assertEquals(row.get("variable_code"), actual.path("variableCode").asString()),
            () -> assertEquals(row.get("variable_name"), actual.path("variableName").asString()),
            () -> assertEquals(row.get("source_type"), actual.path("sourceType").asString()),
            () -> assertEquals(row.get("value_type"), actual.path("valueType").asString()),
            () -> assertEquals(row.get("point_code"), actual.path("pointCode").asString()),
            () -> assertEquals(List.of(VariableUsage.FLOW_CONDITION.getCode(), VariableUsage.RULE_CONDITION.getCode()),
                actual.path("usages").valueStream().map(JsonNode::asString).toList(), "usages"),
            () -> assertTrue(actual.path("systemManaged").asBoolean(), "systemManaged"),
            () -> assertEquals(row.get("status"), actual.path("status").asString()),
            () -> assertEquals(row.get("version").toString(), actual.path("version").asString()),
            () -> assertEquals(null, text(actual, "numericUnit")),
            () -> assertEquals(null, text(actual, "currencyCode")),
            () -> assertEquals(null, text(actual, "currencyVariableCode")),
            () -> assertEquals(row.get("format_code"), actual.path("formatCode").asString()),
            () -> assertEquals(null, text(actual, "allowedValuesVariableCode")),
            () -> assertEquals(null, text(actual, "currentValue")),
            () -> assertEquals(null, text(actual, "memberCount")),
            () -> assertTrue(actual.path("previewMembers").isArray() && actual.path("previewMembers").isEmpty()),
            () -> assertEquals(time(RuleSetStoreChecker.instant(row.get("update_time"))),
                actual.path("updateTime").asString()));
    }

    /** 当前变量读取时间为 UTC 微秒，且在本次请求窗口内。 */
    private void assertValuesAsOf(JsonNode body) {
        String raw = body.path("valuesAsOf").asString("");
        assertTrue(raw.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{6}Z"), "valuesAsOf 格式：" + raw);
        Instant valuesAsOf = Instant.parse(raw);
        assertFalse(valuesAsOf.isBefore(requestedAfter.truncatedTo(java.time.temporal.ChronoUnit.MICROS)),
            "valuesAsOf 早于请求");
        assertFalse(valuesAsOf.isAfter(respondedBefore), "valuesAsOf 晚于响应");
    }

    private static Map<String, Object> operand(OperandRole role, OperandSource source, String fieldCode,
        String variableCode, String literalValue) {
        java.util.HashMap<String, Object> value = new java.util.HashMap<>();
        value.put("operandRole", role.getCode());
        value.put("sourceType", source.getCode());
        value.put("fieldCode", fieldCode);
        value.put("variableCode", variableCode);
        value.put("literalValue", literalValue);
        return value;
    }
}
