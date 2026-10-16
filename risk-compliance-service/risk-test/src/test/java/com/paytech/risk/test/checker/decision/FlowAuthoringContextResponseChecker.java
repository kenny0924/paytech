package com.paytech.risk.test.checker.decision;

import static com.paytech.risk.api.enums.RiskDecisionConfigurationErrorCode.RISK_DECISION_CONFIGURATION_NOT_FOUND;
import static com.paytech.risk.test.checker.decision.FlowGraphChecker.assertFields;
import static com.paytech.risk.test.checker.decision.FlowGraphChecker.text;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;
import com.paytech.risk.test.builder.decision.RuleSetBuilder;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 流程配置能力目录的 HTTP 响应 Checker
 * <li>决策点、合同、容量上限等固定能力逐字段核对；输入字段与节点输出按变量编码顺序逐项核对。</li>
 *
 * @author Kenny
 */
public final class FlowAuthoringContextResponseChecker
    extends AbstractHttpResponseChecker<FlowAuthoringContextResponseChecker> {

    /** 一个可用于流程条件的变量投影；输入字段的 nodeType 为 null，节点输出没有 literalDomain。 */
    public record ExpectedField(String nodeType, String fieldCode, String label, String valueType, boolean required,
        List<String> operators, String literalDomain, String variableCode) {
    }

    public FlowAuthoringContextResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper,
        String requestId) {
        super(result, mapper, requestId);
    }

    @Override
    protected FlowAuthoringContextResponseChecker self() {
        return this;
    }

    /** CHECKOUT_PREPARE 的固定能力、给定输入字段与节点输出。 */
    public FlowAuthoringContextResponseChecker assertCatalog(List<ExpectedField> fields, List<ExpectedField> outputs) {
        assertStatus(200);
        JsonNode body = body();
        assertFields(body, "supportedPoints", "pointCode", "inputContractCode", "inputContractVersion",
            "graphSchemaVersion", "fields", "nodeOutputs", "limits", "descriptorCode", "conditionSchemaVersion",
            "variableQueryContext");
        JsonNode points = body.path("supportedPoints");
        JsonNode limits = body.path("limits");
        JsonNode query = body.path("variableQueryContext");
        assertFields(points.path(0), "pointCode", "pointName");
        assertFields(limits, "bodyBytes", "nodes", "edges", "conditionBytes", "conditionNodes", "conditionDepth");
        assertFields(query, "pointCode", "inputContractCode", "inputContractVersion", "usage");
        assertAll("配置能力", () -> assertEquals(1, points.size(), "supportedPoints 数量"),
            () -> assertEquals(RuleSetBuilder.POINT_CODE, text(points.path(0).path("pointCode"))),
            () -> assertEquals("Checkout Prepare", text(points.path(0).path("pointName"))),
            () -> assertEquals(RuleSetBuilder.POINT_CODE, text(body.path("pointCode"))),
            () -> assertEquals(RuleSetBuilder.INPUT_CONTRACT_CODE, text(body.path("inputContractCode"))),
            () -> assertEquals(RuleSetBuilder.CURRENT_CONTRACT_VERSION, body.path("inputContractVersion").asInt()),
            () -> assertEquals(1, body.path("graphSchemaVersion").asInt()),
            () -> assertEquals(65536, limits.path("bodyBytes").asInt()),
            () -> assertEquals(50, limits.path("nodes").asInt()),
            () -> assertEquals(100, limits.path("edges").asInt()),
            () -> assertEquals(8192, limits.path("conditionBytes").asInt()),
            () -> assertEquals(32, limits.path("conditionNodes").asInt()),
            () -> assertEquals(8, limits.path("conditionDepth").asInt()),
            () -> assertEquals("RISK_VARIABLE_AUTHORING", text(body.path("descriptorCode"))),
            () -> assertEquals(2, body.path("conditionSchemaVersion").asInt()),
            () -> assertEquals(RuleSetBuilder.POINT_CODE, text(query.path("pointCode"))),
            () -> assertEquals(RuleSetBuilder.INPUT_CONTRACT_CODE, text(query.path("inputContractCode"))),
            () -> assertEquals(RuleSetBuilder.CURRENT_CONTRACT_VERSION, query.path("inputContractVersion").asInt()),
            () -> assertEquals("FLOW_CONDITION", text(query.path("usage"))));
        assertItems(body.path("fields"), fields, true);
        assertItems(body.path("nodeOutputs"), outputs, false);
        return this;
    }

    /** 决策点不受支持或不存在。 */
    public FlowAuthoringContextResponseChecker assertNotFound() {
        return assertProblem(404, RISK_DECISION_CONFIGURATION_NOT_FOUND.getCode());
    }

    private static void assertItems(JsonNode actual, List<ExpectedField> expected, boolean input) {
        assertEquals(expected.size(), actual.size(), (input ? "fields" : "nodeOutputs") + " 数量不符");
        for (int i = 0; i < expected.size(); i++) {
            ExpectedField field = expected.get(i);
            JsonNode item = actual.get(i);
            if (input) {
                assertFields(item, "fieldCode", "label", "valueType", "required", "operators", "literalDomain",
                    "variableCode");
            } else {
                assertFields(item, "nodeType", "fieldCode", "label", "valueType", "required", "operators",
                    "variableCode");
            }
            List<String> operators = new ArrayList<>();
            item.path("operators").forEach(op -> operators.add(op.asString()));
            assertAll("变量 " + field.variableCode(),
                () -> assertEquals(field.variableCode(), text(item.path("variableCode"))),
                () -> assertEquals(field.fieldCode(), text(item.path("fieldCode"))),
                () -> assertEquals(field.label(), text(item.path("label"))),
                () -> assertEquals(field.valueType(), text(item.path("valueType"))),
                () -> assertEquals(field.required(), item.path("required").asBoolean()),
                () -> assertEquals(field.operators(), operators),
                () -> {
                    if (input) {
                        assertEquals(field.literalDomain(), text(item.path("literalDomain")));
                    } else {
                        assertEquals(field.nodeType(), text(item.path("nodeType")));
                    }
                });
        }
    }
}
