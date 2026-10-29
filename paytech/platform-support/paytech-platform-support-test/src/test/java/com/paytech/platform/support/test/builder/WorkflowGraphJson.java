package com.paytech.platform.support.test.builder;

import java.util.List;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * 流程图 graph_json 与表单 JSON 的测试构造器（技术方案 3.1–3.2）；只拼数据，不校验语义，语义由被测发布校验判定。
 *
 * <p>节点方法追加节点，{@link #edge} 追加连线；{@link #action}、{@link #approval}、{@link #notify}、{@link #callback}
 * 作用于最近一次追加的节点。</p>
 */
public final class WorkflowGraphJson {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final ObjectNode root = NODES.objectNode();
    private final ArrayNode nodes = root.putArray("nodes");
    private final ArrayNode edges = root.putArray("edges");
    private ObjectNode current;

    private WorkflowGraphJson() {
    }

    /** 带 START 节点 start 的新图。 */
    public static WorkflowGraphJson graph() {
        WorkflowGraphJson graph = new WorkflowGraphJson();
        graph.node("start", "START", "开始");
        return graph;
    }

    /** 追加 END 节点。 */
    public WorkflowGraphJson end(String key) {
        return node(key, "END", "结束");
    }

    /** 追加 TASK 节点。 */
    public WorkflowGraphJson task(String key, String name) {
        node(key, "TASK", name);
        current.putArray("actions");
        return this;
    }

    /** 追加并行拆分节点。 */
    public WorkflowGraphJson split(String key) {
        return node(key, "PARALLEL_SPLIT", "并行拆分");
    }

    /** 追加并行汇合节点。 */
    public WorkflowGraphJson join(String key) {
        return node(key, "PARALLEL_JOIN", "并行汇合");
    }

    /**
     * 追加排他网关：第一条连线满足 variable == value，否则走默认连线。
     *
     * @param key 节点 key
     * @param edgeKey 条件连线
     * @param variable 变量名
     * @param value 比较值
     * @param defaultEdgeKey 默认连线
     */
    public WorkflowGraphJson exclusive(String key, String edgeKey, String variable, String value,
        String defaultEdgeKey) {
        return exclusive(key, edgeKey, variable, "EQ", value, defaultEdgeKey);
    }

    /**
     * 追加排他网关：第一条连线满足 variable operator value，否则走默认连线。
     *
     * @param key 节点 key
     * @param edgeKey 条件连线
     * @param variable 变量名
     * @param operator 比较符，如 EQ、CONTAINS
     * @param value 比较值
     * @param defaultEdgeKey 默认连线
     */
    public WorkflowGraphJson exclusive(String key, String edgeKey, String variable, String operator, String value,
        String defaultEdgeKey) {
        node(key, "EXCLUSIVE", "条件判断");
        ObjectNode condition = current.putArray("conditions").addObject();
        condition.put("edgeKey", edgeKey);
        ObjectNode clause = condition.putArray("all").addObject();
        clause.put("variable", variable);
        clause.put("operator", operator);
        clause.put("value", value);
        current.put("defaultEdgeKey", defaultEdgeKey);
        return this;
    }

    /** 当前 TASK 的审批配置。 */
    public WorkflowGraphJson approval(String mode, long... approverIds) {
        ObjectNode approval = current.putObject("approval");
        approval.put("mode", mode);
        ArrayNode ids = approval.putArray("approverIds");
        for (long id : approverIds) {
            ids.add(Long.toString(id));
        }
        return this;
    }

    /** 当前 TASK 追加动作。 */
    public WorkflowGraphJson action(String key, long templateId, long executorId, boolean required) {
        ObjectNode action = ((ArrayNode) current.get("actions")).addObject();
        action.put("key", key);
        action.put("templateId", Long.toString(templateId));
        action.put("required", required);
        action.put("executorId", Long.toString(executorId));
        return this;
    }

    /** 当前 TASK 最近追加的动作增加一个输入映射（VARIABLE 或 CONSTANT）。 */
    public WorkflowGraphJson input(String key, String source, String value) {
        ArrayNode actions = (ArrayNode) current.get("actions");
        ObjectNode action = (ObjectNode) actions.get(actions.size() - 1);
        ObjectNode inputs = action.has("inputs") ? (ObjectNode) action.get("inputs") : action.putObject("inputs");
        inputs.putObject(key).put("source", source).put("value", value);
        return this;
    }

    /** 当前节点追加站内与邮件通知。 */
    public WorkflowGraphJson notify(String trigger, String... recipients) {
        ObjectNode rule = rules(current).addObject();
        rule.put("trigger", trigger);
        rule.put("type", "NOTIFY");
        ArrayNode targets = rule.putArray("recipients");
        List.of(recipients).forEach(targets::add);
        rule.putArray("channels").add("INBOX").add("EMAIL");
        return this;
    }

    /** 当前节点追加业务回调。 */
    public WorkflowGraphJson callback(String trigger, String callbackCode) {
        ObjectNode rule = rules(current).addObject();
        rule.put("trigger", trigger);
        rule.put("type", "CALLBACK");
        rule.put("callbackCode", callbackCode);
        return this;
    }

    /** 实例级业务回调（流程设置）。 */
    public WorkflowGraphJson instanceCallback(String trigger, String callbackCode) {
        ObjectNode settings = root.has("settings") ? (ObjectNode) root.get("settings") : root.putObject("settings");
        ObjectNode rule = rules(settings).addObject();
        rule.put("trigger", trigger);
        rule.put("type", "CALLBACK");
        rule.put("callbackCode", callbackCode);
        return this;
    }

    /** 追加连线。 */
    public WorkflowGraphJson edge(String key, String from, String to) {
        ObjectNode edge = edges.addObject();
        edge.put("key", key);
        edge.put("from", from);
        edge.put("to", to);
        return this;
    }

    /** 依次连接节点，连线 key 为 from_to。 */
    public WorkflowGraphJson chain(String... keys) {
        for (int index = 1; index < keys.length; index++) {
            edge(keys[index - 1] + "_" + keys[index], keys[index - 1], keys[index]);
        }
        return this;
    }

    /** 构造的 graph_json。 */
    public JsonNode build() {
        return root.deepCopy();
    }

    /** 空启动表单。 */
    public static JsonNode emptyForm() {
        ObjectNode form = NODES.objectNode();
        form.putArray("fields");
        return form;
    }

    /** 只含一个必填 TEXT 字段的表单。 */
    public static JsonNode textForm(String key) {
        ObjectNode form = NODES.objectNode();
        ObjectNode field = form.putArray("fields").addObject();
        field.put("key", key);
        field.put("label", key);
        field.put("type", "TEXT");
        field.put("required", true);
        return form;
    }

    /** 只含一个 ENUM 字段的表单，选项为给定值。 */
    public static JsonNode enumForm(String key, boolean required, String... values) {
        ObjectNode form = NODES.objectNode();
        ObjectNode field = form.putArray("fields").addObject();
        field.put("key", key);
        field.put("label", key);
        field.put("type", "ENUM");
        field.put("required", required);
        ArrayNode options = field.putObject("rules").putArray("options");
        for (String value : values) {
            options.addObject().put("value", value).put("label", value);
        }
        return form;
    }

    /** 人工确认模板配置。 */
    public static JsonNode confirmConfig(boolean noteRequired) {
        return NODES.objectNode().put("instruction", "确认已完成").put("noteRequired", noteRequired);
    }

    /** 单字段对象。 */
    public static JsonNode values(String key, String value) {
        return NODES.objectNode().put(key, value);
    }

    /** 空对象。 */
    public static JsonNode empty() {
        return NODES.objectNode();
    }

    private WorkflowGraphJson node(String key, String type, String name) {
        current = nodes.addObject();
        current.put("key", key);
        current.put("type", type);
        current.put("name", name);
        return this;
    }

    private static ArrayNode rules(ObjectNode owner) {
        return owner.has("notifications") ? (ArrayNode) owner.get("notifications") : owner.putArray("notifications");
    }
}
