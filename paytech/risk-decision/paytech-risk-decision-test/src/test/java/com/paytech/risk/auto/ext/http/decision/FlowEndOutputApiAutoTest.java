package com.paytech.risk.auto.ext.http.decision;

import static com.paytech.foundation.common.error.CommonErrorCode.INVALID_REQUEST;
import static com.paytech.foundation.common.error.CommonErrorCode.VALIDATION_ERROR;
import static com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder.action;
import static com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder.predicate;
import static com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder.rule;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;

import com.paytech.foundation.common.http.ApiHeaderNames;
import com.paytech.risk.decision.api.DecisionFlowManagementApi;
import com.paytech.risk.decision.api.evaluation.request.PaymentAttemptRiskInput;
import com.paytech.risk.decision.domain.enums.AuthenticationRequirement;
import com.paytech.risk.decision.domain.enums.DecisionValue;
import com.paytech.risk.test.base.AbstractPaymentRiskApiTest;
import com.paytech.risk.test.builder.decision.FlowBuilder.GraphSpec;
import com.paytech.risk.test.builder.decision.FlowBuilder.NodeSpec;
import com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder;
import com.paytech.risk.test.builder.decision.SaveFlowRequestBuilder;
import com.paytech.risk.test.checker.decision.SaveFlowResponseChecker;

import tools.jackson.databind.JsonNode;

/**
 * 决策流结束节点附加输出：支付决策点声明认证要求输出，由放行结束节点配置
 * <li>正常流程：放行结束节点保存 AUTHENTICATION_REQUIREMENT 后，流程详情按节点返回输出；未配置的放行结束节点保存时补齐合同默认值；非放行节点不返回该字段。</li>
 * <li>创作上下文：支付点返回附加输出声明（编码、名称、可选值与名称、默认值、只允许放行结束节点）；结账点没有声明时不输出该键。</li>
 * <li>参数：非放行结束节点或规则集节点配置输出、输出编码未声明、取值不允许返回 422 并指出字段；输出值不是字符串返回 400。</li>
 *
 * @see DecisionFlowManagementApi
 */
class FlowEndOutputApiAutoTest extends AbstractPaymentRiskApiTest {
    private static final String FLOWS = "/decision-flows/v1/flows";

    private GraphSpec graph;

    /** 每例准备两段流程（含“放行（需要 3DS）”结束节点）。 */
    @BeforeEach
    void setUpClassMocks() {
        String reason = rejectionReason();
        graph = twoStageGraph(ruleSet("T_BLOCK", "REVIEW_PAYMENT"), ruleSet("T_AUTH", "ALLOW_PAYMENT"), reason);
    }

    /** 保存后详情按节点返回输出，未配置的放行结束节点补齐默认值。 */
    @Test
    void test_normalFlow() {
        JsonNode saved = postOk(FLOWS, SaveFlowRequestBuilder.template(newCode("T_FLOW")).pointCode(PAYMENT).graph(graph)
            .build());

        JsonNode detail = getOk(FLOWS + "/" + saved.path("flowId").asString());
        Map<String, Map<String, String>> outputs = new TreeMap<>();
        detail.path("graph").path("nodes").forEach(node -> {
            if (node.has("outputs")) {
                Map<String, String> values = new TreeMap<>();
                node.path("outputs").properties().forEach(entry -> values.put(entry.getKey(), entry.getValue().asString()));
                outputs.put(node.path("nodeKey").asString(), values);
            }
        });
        assertEquals(Map.of(
            "allow", Map.of(PaymentAttemptRiskInput.AUTHENTICATION_REQUIREMENT,
                AuthenticationRequirement.NOT_REQUIRED.getCode()),
            "allow_3ds", Map.of(PaymentAttemptRiskInput.AUTHENTICATION_REQUIREMENT,
                AuthenticationRequirement.THREE_DS_REQUIRED.getCode())), outputs);
    }

    /** 创作上下文按风控点返回附加输出声明。 */
    @Test
    void test_normalFlow_authoringContext() {
        JsonNode payment = getOk("/decision-flows/v1/authoring-context?pointCode=" + PAYMENT);
        JsonNode checkout = getOk("/decision-flows/v1/authoring-context?pointCode=CHECKOUT_PREPARE");

        JsonNode output = payment.path("outputs").path(0);
        assertAll("附加输出声明", () -> assertEquals(1, payment.path("outputs").size()),
            () -> assertEquals(PaymentAttemptRiskInput.AUTHENTICATION_REQUIREMENT, output.path("outputCode").asString()),
            () -> assertEquals("认证要求", output.path("name").asString()),
            () -> assertEquals(AuthenticationRequirement.NOT_REQUIRED.getCode(), output.path("defaultValue").asString()),
            () -> assertEquals(List.of(DecisionValue.ALLOW.getCode()),
                List.of(output.path("allowedEndDecisions").path(0).asString())),
            () -> assertEquals(Map.of(AuthenticationRequirement.NOT_REQUIRED.getCode(), "无需认证",
                AuthenticationRequirement.THREE_DS_REQUIRED.getCode(), "需要 3DS"), values(output)),
            () -> assertFalse(checkout.has("outputs"), "结账点没有声明时不输出该键"));
    }

    /** 声明中的可选值编码到名称。 */
    private static Map<String, String> values(JsonNode output) {
        Map<String, String> values = new TreeMap<>();
        output.path("allowedValues").forEach(value -> values.put(value.path("value").asString(), value.path("name").asString()));
        return values;
    }

    /** 输出配置不符合决策点合同。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidOutputs")
    void test_invalidParameter(String scenario, String field, Map.Entry<String, UnaryOperator<NodeSpec>> change) {
        save(withNode(change)).assertProblem(422, VALIDATION_ERROR.getCode()).assertFieldError(field);
    }

    /** 输出值不是字符串：在协议层拒绝。 */
    @Test
    void test_invalidParameter_nonStringValue() {
        String json = objectMapper.writeValueAsString(SaveFlowRequestBuilder.template(newCode("T_FLOW")).pointCode(PAYMENT)
            .graph(graph).build()).replace("\"THREE_DS_REQUIRED\"", "true");
        HttpHeaders headers = headers();
        headers.set(HttpHeaders.CONTENT_TYPE, "application/json");

        new SaveFlowResponseChecker(http.post(FLOWS, headers, json), objectMapper,
            headers.getFirst(ApiHeaderNames.REQUEST_ID)).assertProblem(400, INVALID_REQUEST.getCode());
    }

    static Stream<Arguments> invalidOutputs() {
        Map<String, String> threeDs = Map.of(PaymentAttemptRiskInput.AUTHENTICATION_REQUIREMENT,
            AuthenticationRequirement.THREE_DS_REQUIRED.getCode());
        return Stream.of(
            Arguments.of("审核结束节点配置输出", "graph.nodes[4].outputs", change("review", node -> outputs(node, threeDs))),
            Arguments.of("规则集节点配置输出", "graph.nodes[1].outputs", change("block", node -> outputs(node, threeDs))),
            Arguments.of("输出编码未声明", "graph.nodes[6].outputs.RISK_SCORE",
                change("allow_3ds", node -> outputs(node, Map.of("RISK_SCORE", "HIGH")))),
            Arguments.of("输出取值不允许", "graph.nodes[6].outputs." + PaymentAttemptRiskInput.AUTHENTICATION_REQUIREMENT,
                change("allow_3ds", node -> outputs(node,
                    Map.of(PaymentAttemptRiskInput.AUTHENTICATION_REQUIREMENT, "CHALLENGE_REQUIRED")))));
    }

    /** 冻结的单规则支付规则集。 */
    private long ruleSet(String prefix, String actionCode) {
        return frozenRuleSet(RuleSetCreateRequestBuilder.template(newCode(prefix)).pointCode(PAYMENT)
            .rules(rule("LARGE_AMOUNT", "Large amount", 10, action(actionCode), predicate("root", null, 1, "GT",
                variableOperand("PAY_INPUT_AMOUNT", "NUMBER"), money("1000"))))
            .build());
    }

    /** 改写一个节点后的流程。 */
    private GraphSpec withNode(Map.Entry<String, UnaryOperator<NodeSpec>> change) {
        List<NodeSpec> nodes = new ArrayList<>();
        graph.nodes().forEach(node -> nodes.add(node.nodeKey().equals(change.getKey()) ? change.getValue().apply(node) : node));
        return new GraphSpec(nodes, graph.edges());
    }

    /** 保存流程。 */
    private SaveFlowResponseChecker save(GraphSpec value) {
        HttpHeaders headers = headers();
        return new SaveFlowResponseChecker(http.post(FLOWS, headers,
            SaveFlowRequestBuilder.template(newCode("T_FLOW")).pointCode(PAYMENT).graph(value).build()), objectMapper,
            headers.getFirst(ApiHeaderNames.REQUEST_ID));
    }

    /** GET 并要求成功，返回响应体。 */
    private JsonNode getOk(String path) {
        HttpHeaders headers = headers();
        var result = http.get(path, headers);
        assertEquals(200, result.getStatus().value(), path);
        return objectMapper.readTree(result.getResponseBody());
    }

    /** 参数化场景：按节点键改写。 */
    private static Map.Entry<String, UnaryOperator<NodeSpec>> change(String nodeKey, UnaryOperator<NodeSpec> change) {
        return Map.entry(nodeKey, change);
    }

    /** 替换节点输出。 */
    private static NodeSpec outputs(NodeSpec node, Map<String, String> outputs) {
        return new NodeSpec(node.nodeKey(), node.type(), node.name(), node.x(), node.y(), node.ruleSetVersionId(),
            node.decision(), node.reasonCode(), node.reasonMessage(), outputs);
    }
}
