package com.paytech.risk.test.builder;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** 最小有效完整流程：开始直接到明确结束；仅构造请求，不隐式调用业务。 */
public final class DecisionFlowRequestBuilder {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private DecisionFlowRequestBuilder() { }

    public static ObjectNode template(String code) {
        ObjectNode request = JSON.createObjectNode().put("pointCode", "CHECKOUT_PREPARE").put("flowCode", code)
            .put("flowName", "配置测试流程").putNull("baseFlowVersionId").putNull("expectedVersion").put("changeReason", "配置初版");
        ObjectNode graph = request.putObject("graph").put("schemaVersion", 1);
        graph.putArray("nodes").add(node("start", "START", null)).add(node("finish", "END", "ALLOW"));
        graph.putArray("edges").add(JSON.createObjectNode().put("edgeKey", "next").put("sourceNodeKey", "start")
            .put("targetNodeKey", "finish").put("type", "UNCONDITIONAL").put("priority", 0).put("name", "继续").putNull("condition"));
        return request;
    }

    public static ObjectNode node(String key, String type, String decision) {
        ObjectNode node = JSON.createObjectNode().put("nodeKey", key).put("type", type).put("name", key)
            .putNull("ruleSetVersionId").put("decisionCode", decision).putNull("reasonCode").putNull("reasonMessage");
        node.putObject("position").put("x", "-120").put("y", "0");
        return node;
    }
}
