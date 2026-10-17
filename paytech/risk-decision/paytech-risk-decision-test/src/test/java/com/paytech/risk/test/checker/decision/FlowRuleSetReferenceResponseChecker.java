package com.paytech.risk.test.checker.decision;

import static com.paytech.risk.decision.api.enums.RiskDecisionConfigurationErrorCode.RISK_DECISION_CONFIGURATION_NOT_FOUND;
import static com.paytech.risk.test.checker.decision.FlowGraphChecker.assertFields;
import static com.paytech.risk.test.checker.decision.FlowGraphChecker.id;
import static com.paytech.risk.test.checker.decision.FlowGraphChecker.text;
import static com.paytech.risk.test.checker.decision.FlowGraphChecker.time;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;
import com.paytech.risk.decision.domain.enums.RuleSetManagementStatus;
import com.paytech.risk.decision.domain.enums.RuleSetVersionStatus;
import com.paytech.risk.test.builder.decision.RuleSetBuilder;
import com.paytech.risk.test.mocker.decision.RuleSetMocker.MockedRuleSet;
import com.paytech.risk.test.mocker.decision.RuleSetMocker.MockedVersion;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 已保存来源节点的准确规则引用 HTTP 响应 Checker
 * <li>来源坐标原样回显；规则集详情逐字段核对插入的版本事实与 {@link RuleSetBuilder#MINIMAL_RULE}。</li>
 *
 * @author Kenny
 */
public final class FlowRuleSetReferenceResponseChecker
    extends AbstractHttpResponseChecker<FlowRuleSetReferenceResponseChecker> {

    /** 期望的来源坐标与被引用规则版本。 */
    public record ExpectedSource(String sourceType, long sourceVersionId, String nodeKey) {
    }

    public FlowRuleSetReferenceResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper,
        String requestId) {
        super(result, mapper, requestId);
    }

    @Override
    protected FlowRuleSetReferenceResponseChecker self() {
        return this;
    }

    /**
     * 成功响应：来源坐标、规则集管理状态及准确规则版本详情。
     *
     * @param pointVersionId 规则版本的旧编排伴随点版本，无则为 null
     * @param ruleNodeId 版本中唯一规则判断节点的数据库 ID
     */
    public FlowRuleSetReferenceResponseChecker assertReference(ExpectedSource source, MockedRuleSet ruleSet,
        int versionNo, RuleSetManagementStatus ruleSetStatus, Long pointVersionId, long ruleNodeId, Instant from,
        Instant to) {
        assertStatus(200);
        JsonNode body = body();
        MockedVersion version = ruleSet.version(versionNo);
        boolean frozen = version.spec().status() == RuleSetVersionStatus.FROZEN;
        assertFields(body, "sourceType", "sourceVersionId", "nodeKey", "ruleSetStatus", "ruleSet");
        JsonNode detail = body.path("ruleSet");
        assertFields(detail, "ruleSetVersionId", "pointVersionId", "pointCode", "ruleSetCode", "ruleSetName",
            "versionNo", "status", "contentHash", "createdBy", "changeReason", "createTime", "frozenBy", "frozenTime",
            "rules", "operandFormat", "currentVariables", "valuesAsOf", "variableIssues");
        assertAll("来源坐标", () -> assertEquals(source.sourceType(), text(body.path("sourceType"))),
            () -> assertEquals(Long.toString(source.sourceVersionId()), text(body.path("sourceVersionId"))),
            () -> assertEquals(source.nodeKey(), text(body.path("nodeKey"))),
            () -> assertEquals(ruleSetStatus.getCode(), text(body.path("ruleSetStatus"))));
        assertAll("规则版本详情",
            () -> assertEquals(Long.toString(version.ruleSetVersionId()), text(detail.path("ruleSetVersionId"))),
            () -> assertEquals(id(pointVersionId), text(detail.path("pointVersionId"))),
            () -> assertEquals(RuleSetBuilder.POINT_CODE, text(detail.path("pointCode"))),
            () -> assertEquals(ruleSet.ruleSetCode(), text(detail.path("ruleSetCode"))),
            () -> assertEquals(version.spec().ruleSetName(), text(detail.path("ruleSetName"))),
            () -> assertEquals(versionNo, detail.path("versionNo").asInt()),
            () -> assertEquals(version.spec().status().getCode(), text(detail.path("status"))),
            () -> assertEquals(RuleSetBuilder.contentHash(ruleSet.ruleSetCode(), version.spec().ruleSetName(),
                versionNo), text(detail.path("contentHash"))),
            () -> assertEquals(Long.toString(version.spec().createdBy()), text(detail.path("createdBy"))),
            () -> assertEquals(version.spec().changeReason(), text(detail.path("changeReason"))),
            () -> assertEquals(time(version.spec().createTime()), text(detail.path("createTime"))),
            () -> assertEquals(frozen ? Long.toString(version.spec().createdBy()) : null,
                text(detail.path("frozenBy"))),
            () -> assertEquals(frozen ? time(version.spec().createTime()) : null, text(detail.path("frozenTime"))),
            () -> assertEquals("LEGACY", text(detail.path("operandFormat"))),
            () -> assertEquals(0, detail.path("currentVariables").size(), "最小规则不引用共享变量"),
            () -> assertEquals(0, detail.path("variableIssues").size(), "不应有变量问题"));
        FlowGraphChecker.assertTimeWithin(text(detail.path("valuesAsOf")), from, to, "valuesAsOf");
        assertMinimalRule(detail.path("rules"), ruleNodeId);
        return this;
    }

    /** 来源、节点或规则不存在。 */
    public FlowRuleSetReferenceResponseChecker assertNotFound() {
        return assertProblem(404, RISK_DECISION_CONFIGURATION_NOT_FOUND.getCode());
    }

    /** 唯一规则：country_code 非空即 ALLOW_CHECKOUT。 */
    private static void assertMinimalRule(JsonNode rules, long ruleNodeId) {
        assertEquals(1, rules.size(), "规则数量不符");
        JsonNode rule = rules.get(0);
        assertFields(rule, "ruleCode", "ruleName", "priorityNo", "enabledFlag", "reasonCode", "conditionNodes", "action",
            "reasonMessage", "additionalActions");
        JsonNode node = rule.path("conditionNodes").path(0);
        JsonNode operand = node.path("operands").path(0);
        assertFields(node, "nodeKey", "parentNodeKey", "sequenceNo", "nodeType", "groupOperator", "comparisonOperator",
            "operands");
        assertFields(operand, "operandRole", "valueSequence", "sourceType", "fieldCode", "valueType", "literalValue",
            "variableCode", "numericUnit", "currencyCode", "currencyVariableCode");
        assertFields(rule.path("action"), "actionCode", "actionVersionNo");
        assertAll("最小规则", () -> assertEquals("MOCK_RULE", text(rule.path("ruleCode"))),
            () -> assertEquals("Mock rule", text(rule.path("ruleName"))),
            () -> assertEquals(10, rule.path("priorityNo").asInt()),
            () -> assertTrue(rule.path("enabledFlag").asBoolean()),
            () -> assertTrue(rule.path("reasonCode").isNull()),
            () -> assertTrue(rule.path("reasonMessage").isNull()),
            () -> assertEquals(0, rule.path("additionalActions").size()),
            () -> assertEquals("ALLOW_CHECKOUT", text(rule.path("action").path("actionCode"))),
            () -> assertEquals(1, rule.path("action").path("actionVersionNo").asInt()),
            () -> assertEquals(1, rule.path("conditionNodes").size()),
            () -> assertEquals(Long.toString(ruleNodeId), text(node.path("nodeKey"))),
            () -> assertTrue(node.path("parentNodeKey").isNull()),
            () -> assertEquals(1, node.path("sequenceNo").asInt()),
            () -> assertEquals("PREDICATE", text(node.path("nodeType"))),
            () -> assertTrue(node.path("groupOperator").isNull()),
            () -> assertEquals("IS_NOT_NULL", text(node.path("comparisonOperator"))),
            () -> assertEquals(1, node.path("operands").size()),
            () -> assertEquals("LEFT", text(operand.path("operandRole"))),
            () -> assertEquals(1, operand.path("valueSequence").asInt()),
            () -> assertEquals("CONTEXT", text(operand.path("sourceType"))),
            () -> assertEquals("country_code", text(operand.path("fieldCode"))),
            () -> assertEquals("STRING", text(operand.path("valueType"))),
            () -> assertTrue(operand.path("literalValue").isNull()),
            () -> assertTrue(operand.path("variableCode").isNull()),
            () -> assertTrue(operand.path("numericUnit").isNull()),
            () -> assertTrue(operand.path("currencyCode").isNull()),
            () -> assertTrue(operand.path("currencyVariableCode").isNull()));
    }
}
