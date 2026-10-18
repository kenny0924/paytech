package com.paytech.risk.test.checker.decision;

import static com.paytech.risk.decision.api.enums.RiskDecisionConfigurationErrorCode.RISK_DECISION_CONFIGURATION_NOT_FOUND;
import static com.paytech.risk.test.checker.decision.FlowGraphChecker.assertFields;
import static com.paytech.risk.test.checker.decision.FlowGraphChecker.text;
import static com.paytech.risk.test.checker.decision.FlowGraphChecker.time;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;
import com.paytech.risk.decision.domain.enums.RuleSetManagementStatus;
import com.paytech.risk.test.builder.decision.RuleSetBuilder;
import com.paytech.risk.test.mocker.decision.FlowMocker.MockedFlow;
import com.paytech.risk.test.mocker.decision.FlowMocker.MockedFlowVersion;
import com.paytech.risk.test.mocker.decision.RuleSetMocker.MockedRuleSet;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 流程当前/准确版本详情的 HTTP 响应 Checker
 * <li>根事实取自插入的流程根，版本事实与配置图取自所选版本；当前变量值只核对查询时刻与问题列表。</li>
 *
 * @author Kenny
 */
public final class FlowDetailResponseChecker extends AbstractHttpResponseChecker<FlowDetailResponseChecker> {

    /** 期望的规则集节点引用。 */
    public record ExpectedReference(String nodeKey, MockedRuleSet ruleSet, int versionNo,
        RuleSetManagementStatus ruleSetStatus) {
    }

    public FlowDetailResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper, String requestId) {
        super(result, mapper, requestId);
    }

    @Override
    protected FlowDetailResponseChecker self() {
        return this;
    }

    /** 成功响应：根事实、所选版本事实及完整配置图逐字段核对，变量问题为空。 */
    public FlowDetailResponseChecker assertDetail(MockedFlow flow, int versionNo) {
        assertStatus(200);
        JsonNode body = body();
        MockedFlowVersion selected = flow.version(versionNo);
        MockedFlowVersion head = flow.current();
        assertFields(body, "flowId", "pointCode", "flowCode", "flowStatus", "currentFlowVersionId", "currentVersionNo",
            "version", "flowVersionId", "versionNo", "flowName", "inputContractCode", "inputContractVersion",
            "createdBy", "createTime", "changeReason", "graph", "ruleSetReferences", "currentVariables", "valuesAsOf",
            "variableIssues");
        assertAll("流程详情", () -> assertEquals(Long.toString(flow.flowId()), text(body.path("flowId"))),
            () -> assertEquals(RuleSetBuilder.POINT_CODE, text(body.path("pointCode"))),
            () -> assertEquals(flow.flowCode(), text(body.path("flowCode"))),
            () -> assertEquals(flow.status().getCode(), text(body.path("flowStatus"))),
            () -> assertEquals(Long.toString(head.flowVersionId()), text(body.path("currentFlowVersionId"))),
            () -> assertEquals(head.versionNo(), body.path("currentVersionNo").asInt()),
            () -> assertEquals(Long.toString(flow.version()), text(body.path("version"))),
            () -> assertEquals(Long.toString(selected.flowVersionId()), text(body.path("flowVersionId"))),
            () -> assertEquals(versionNo, body.path("versionNo").asInt()),
            () -> assertEquals(selected.spec().flowName(), text(body.path("flowName"))),
            () -> assertEquals("ORDER_PREPARATION", text(body.path("inputContractCode"))),
            () -> assertEquals(4, body.path("inputContractVersion").asInt()),
            () -> assertEquals(Long.toString(selected.spec().createdBy()), text(body.path("createdBy"))),
            () -> assertEquals(time(selected.spec().createTime()), text(body.path("createTime"))),
            () -> assertEquals(selected.spec().changeReason(), text(body.path("changeReason"))),
            () -> assertEquals(0, body.path("variableIssues").size(), "不应有变量问题"));
        FlowGraphChecker.assertGraph(body.path("graph"), selected.spec().graph());
        return this;
    }

    /** 规则集节点引用按图中出现顺序（节点键排序）逐项核对。 */
    public FlowDetailResponseChecker assertReferences(ExpectedReference... expected) {
        JsonNode references = body().path("ruleSetReferences");
        assertEquals(expected.length, references.size(), "规则集引用数量不符");
        for (int i = 0; i < expected.length; i++) {
            ExpectedReference reference = expected[i];
            FlowGraphChecker.assertReference(references.get(i), reference.nodeKey(), reference.ruleSet(),
                reference.versionNo(), reference.ruleSetStatus());
        }
        return this;
    }

    /** 没有引用任何变量时当前变量为空，查询时刻取自本次请求期间。 */
    public FlowDetailResponseChecker assertNoVariables(Instant from, Instant to) {
        assertEquals(0, body().path("currentVariables").size(), "不应有当前变量");
        FlowGraphChecker.assertTimeWithin(text(body().path("valuesAsOf")), from, to, "valuesAsOf");
        return this;
    }

    /** 条件只引用系统节点输出 NODE_MATCHED 时的当前变量投影。 */
    public FlowDetailResponseChecker assertNodeMatchedVariable(Instant updateTime, Instant from, Instant to) {
        JsonNode variables = body().path("currentVariables");
        assertEquals(1, variables.size(), "当前变量数量不符");
        JsonNode variable = variables.get(0);
        assertFields(variable, "variableCode", "variableName", "sourceType", "valueType", "pointCode", "usages",
            "systemManaged", "status", "version", "numericUnit", "currencyCode", "currencyVariableCode", "formatCode",
            "allowedValuesVariableCode", "currentValue", "memberCount", "previewMembers", "updateTime");
        assertAll("NODE_MATCHED", () -> assertEquals("NODE_MATCHED", text(variable.path("variableCode"))),
            () -> assertEquals("本节点是否命中", text(variable.path("variableName"))),
            () -> assertEquals("NODE_OUTPUT", text(variable.path("sourceType"))),
            () -> assertEquals("BOOLEAN", text(variable.path("valueType"))),
            () -> assertEquals(RuleSetBuilder.POINT_CODE, text(variable.path("pointCode"))),
            () -> assertEquals(1, variable.path("usages").size()),
            () -> assertEquals("FLOW_CONDITION", text(variable.path("usages").get(0))),
            () -> assertTrue(variable.path("systemManaged").asBoolean()),
            () -> assertEquals("ACTIVE", text(variable.path("status"))),
            () -> assertEquals("1", text(variable.path("version"))),
            () -> assertTrue(variable.path("numericUnit").isNull()),
            () -> assertTrue(variable.path("currencyCode").isNull()),
            () -> assertTrue(variable.path("currencyVariableCode").isNull()),
            () -> assertEquals("NONE", text(variable.path("formatCode"))),
            () -> assertTrue(variable.path("allowedValuesVariableCode").isNull()),
            () -> assertTrue(variable.path("currentValue").isNull()),
            () -> assertTrue(variable.path("memberCount").isNull()),
            () -> assertEquals(0, variable.path("previewMembers").size()),
            () -> assertEquals(time(updateTime), text(variable.path("updateTime"))));
        FlowGraphChecker.assertTimeWithin(text(body().path("valuesAsOf")), from, to, "valuesAsOf");
        return this;
    }

    /** 流程不存在、当前入口已删除或版本不属于该流程。 */
    public FlowDetailResponseChecker assertNotFound() {
        return assertProblem(404, RISK_DECISION_CONFIGURATION_NOT_FOUND.getCode());
    }
}
