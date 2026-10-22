package com.paytech.platform.support.workflow.domainservice.convert;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.paytech.platform.support.workflow.core.enums.ApprovalMode;
import com.paytech.platform.support.workflow.core.enums.ConditionOperator;
import com.paytech.platform.support.workflow.core.enums.InputMappingSource;
import com.paytech.platform.support.workflow.core.enums.NotificationChannel;
import com.paytech.platform.support.workflow.core.enums.NotificationRecipient;
import com.paytech.platform.support.workflow.core.enums.NotificationRuleType;
import com.paytech.platform.support.workflow.core.enums.NotificationTrigger;
import com.paytech.platform.support.workflow.core.enums.WorkflowNodeType;
import com.paytech.platform.support.workflow.domain.graph.ConditionClause;
import com.paytech.platform.support.workflow.domain.graph.ConditionValue;
import com.paytech.platform.support.workflow.domain.graph.EdgeCondition;
import com.paytech.platform.support.workflow.domain.graph.InputMapping;
import com.paytech.platform.support.workflow.domain.graph.NodeAction;
import com.paytech.platform.support.workflow.domain.graph.NodeApproval;
import com.paytech.platform.support.workflow.domain.graph.NodePosition;
import com.paytech.platform.support.workflow.domain.graph.NotificationRule;
import com.paytech.platform.support.workflow.domain.graph.WorkflowEdge;
import com.paytech.platform.support.workflow.domain.graph.WorkflowGraph;
import com.paytech.platform.support.workflow.domain.graph.WorkflowNode;
import com.paytech.platform.support.workflow.domain.graph.WorkflowSettings;

import tools.jackson.databind.JsonNode;

/**
 * 把流程版本的 graph_json 解析为 {@link WorkflowGraph}（技术方案 3.1）。
 *
 * <p>结构固定：未知属性、类型不符、未知编码一律以 {@link WorkflowJsonFieldException} 拒绝，错误信息带 JSON 路径。
 * 空 key、缺失的审批方式、模板与人员等语义缺口照常解析，由发布校验返回问题列表。</p>
 */
public final class WorkflowGraphJsonReader {

    private static final String ROOT = "graph_json";

    private static final Set<String> ROOT_FIELDS = Set.of("nodes", "edges", "settings");

    private static final Set<String> NODE_FIELDS = Set.of("key", "type", "name", "position", "approval", "actions",
        "conditions", "defaultEdgeKey", "notifications");

    private static final Set<String> EDGE_FIELDS = Set.of("key", "from", "to");

    private static final Set<String> ACTION_FIELDS = Set.of("key", "templateId", "required", "executorId", "inputs");

    private static final Set<String> RULE_FIELDS = Set.of("trigger", "type", "recipients", "userIds", "channels",
        "callbackCode");

    /**
     * 解析 graph_json。
     *
     * @param json graph_json 文本
     * @return 流程图
     * @throws WorkflowJsonFieldException JSON 不合法或结构不符
     */
    public WorkflowGraph read(String json) {
        JsonNode root = StrictJson.parseObject(json, ROOT);
        StrictJson.allowOnly(root, ROOT, ROOT_FIELDS);
        requireArray(root, "nodes");
        requireArray(root, "edges");
        List<WorkflowNode> nodes = StrictJson.array(root, "nodes", ROOT, this::readNode);
        List<WorkflowEdge> edges = StrictJson.array(root, "edges", ROOT, this::readEdge);
        return new WorkflowGraph(nodes, edges, readSettings(StrictJson.present(root, "settings")));
    }

    /**
     * 要求根对象带有数组属性。
     *
     * @param root 根对象
     * @param name 属性名
     */
    private void requireArray(JsonNode root, String name) {
        if (StrictJson.present(root, name) == null) {
            throw new WorkflowJsonFieldException(ROOT + "." + name, "不能为空");
        }
    }

    /**
     * 解析一个节点。
     *
     * @param node 节点 JSON
     * @param path JSON 路径
     * @return 节点
     */
    private WorkflowNode readNode(JsonNode node, String path) {
        StrictJson.object(node, path);
        StrictJson.allowOnly(node, path, NODE_FIELDS);
        WorkflowNodeType type = StrictJson.code(node, "type", path, WorkflowNodeType.class);
        if (type == null) {
            throw new WorkflowJsonFieldException(path + ".type", "不能为空");
        }
        return new WorkflowNode(StrictJson.text(node, "key", path), type, StrictJson.text(node, "name", path),
            readPosition(StrictJson.present(node, "position"), path + ".position"),
            readApproval(StrictJson.present(node, "approval"), path + ".approval"),
            StrictJson.array(node, "actions", path, this::readAction),
            StrictJson.array(node, "conditions", path, this::readCondition),
            StrictJson.text(node, "defaultEdgeKey", path),
            StrictJson.array(node, "notifications", path, this::readRule));
    }

    /**
     * 解析节点位置。
     *
     * @param position 位置 JSON；可能为 null
     * @param path JSON 路径
     * @return 位置或 null
     */
    private NodePosition readPosition(JsonNode position, String path) {
        if (position == null) {
            return null;
        }
        StrictJson.object(position, path);
        StrictJson.allowOnly(position, path, Set.of("x", "y"));
        JsonNode x = StrictJson.present(position, "x");
        JsonNode y = StrictJson.present(position, "y");
        if (x == null || y == null || !x.isNumber() || !y.isNumber()) {
            throw new WorkflowJsonFieldException(path, "x、y 必须是数字");
        }
        return new NodePosition(x.doubleValue(), y.doubleValue());
    }

    /**
     * 解析审批配置。
     *
     * @param approval 审批 JSON；可能为 null
     * @param path JSON 路径
     * @return 审批配置或 null
     */
    private NodeApproval readApproval(JsonNode approval, String path) {
        if (approval == null) {
            return null;
        }
        StrictJson.object(approval, path);
        StrictJson.allowOnly(approval, path, Set.of("mode", "approverIds"));
        return new NodeApproval(StrictJson.code(approval, "mode", path, ApprovalMode.class),
            StrictJson.ids(approval, "approverIds", path));
    }

    /**
     * 解析节点动作。
     *
     * @param action 动作 JSON
     * @param path JSON 路径
     * @return 节点动作
     */
    private NodeAction readAction(JsonNode action, String path) {
        StrictJson.object(action, path);
        StrictJson.allowOnly(action, path, ACTION_FIELDS);
        return new NodeAction(StrictJson.text(action, "key", path), StrictJson.id(action, "templateId", path),
            StrictJson.bool(action, "required", path, true), StrictJson.id(action, "executorId", path),
            readInputs(StrictJson.present(action, "inputs"), path + ".inputs"));
    }

    /**
     * 解析输入参数映射，保持属性顺序。
     *
     * @param inputs 映射 JSON；可能为 null
     * @param path JSON 路径
     * @return 映射
     */
    private Map<String, InputMapping> readInputs(JsonNode inputs, String path) {
        Map<String, InputMapping> result = new LinkedHashMap<>();
        if (inputs == null) {
            return result;
        }
        StrictJson.object(inputs, path);
        for (String name : inputs.propertyNames()) {
            String itemPath = path + "." + name;
            JsonNode item = StrictJson.object(inputs.get(name), itemPath);
            StrictJson.allowOnly(item, itemPath, Set.of("source", "value"));
            InputMappingSource source = StrictJson.code(item, "source", itemPath, InputMappingSource.class);
            if (source == null) {
                throw new WorkflowJsonFieldException(itemPath + ".source", "不能为空");
            }
            result.put(name, new InputMapping(source, StrictJson.text(item, "value", itemPath)));
        }
        return result;
    }

    /**
     * 解析排他网关的一组条件。
     *
     * @param condition 条件 JSON
     * @param path JSON 路径
     * @return 条件组
     */
    private EdgeCondition readCondition(JsonNode condition, String path) {
        StrictJson.object(condition, path);
        StrictJson.allowOnly(condition, path, Set.of("edgeKey", "all"));
        return new EdgeCondition(StrictJson.text(condition, "edgeKey", path),
            StrictJson.array(condition, "all", path, this::readClause));
    }

    /**
     * 解析条件子句。
     *
     * @param clause 子句 JSON
     * @param path JSON 路径
     * @return 子句
     */
    private ConditionClause readClause(JsonNode clause, String path) {
        StrictJson.object(clause, path);
        StrictJson.allowOnly(clause, path, Set.of("variable", "operator", "value"));
        ConditionOperator operator = StrictJson.code(clause, "operator", path, ConditionOperator.class);
        if (operator == null) {
            throw new WorkflowJsonFieldException(path + ".operator", "不能为空");
        }
        return new ConditionClause(StrictJson.text(clause, "variable", path), operator,
            readValue(StrictJson.present(clause, "value"), path + ".value"));
    }

    /**
     * 解析比较值：标量或标量数组，统一保存为文本。
     *
     * @param value 比较值 JSON；可能为 null
     * @param path JSON 路径
     * @return 比较值或 null
     */
    private ConditionValue readValue(JsonNode value, String path) {
        if (value == null) {
            return null;
        }
        if (!value.isArray()) {
            return ConditionValue.scalar(scalarText(value, path));
        }
        List<String> values = new ArrayList<>();
        for (int index = 0; index < value.size(); index++) {
            values.add(scalarText(value.get(index), path + "[" + index + "]"));
        }
        return ConditionValue.listOf(values);
    }

    /**
     * 把标量转成文本：字符串原样，数字取普通十进制，布尔取 true / false。
     *
     * @param value 标量 JSON
     * @param path JSON 路径
     * @return 文本
     */
    private String scalarText(JsonNode value, String path) {
        if (value.isString()) {
            return value.asString();
        }
        if (value.isNumber()) {
            return value.decimalValue().toPlainString();
        }
        if (value.isBoolean()) {
            return Boolean.toString(value.booleanValue());
        }
        throw new WorkflowJsonFieldException(path, "必须是字符串、数字或布尔值");
    }

    /**
     * 解析一条通知与回调规则。
     *
     * @param rule 规则 JSON
     * @param path JSON 路径
     * @return 规则
     */
    private NotificationRule readRule(JsonNode rule, String path) {
        StrictJson.object(rule, path);
        StrictJson.allowOnly(rule, path, RULE_FIELDS);
        NotificationTrigger trigger = StrictJson.code(rule, "trigger", path, NotificationTrigger.class);
        NotificationRuleType type = StrictJson.code(rule, "type", path, NotificationRuleType.class);
        if (trigger == null || type == null) {
            throw new WorkflowJsonFieldException(path, "trigger 与 type 不能为空");
        }
        List<NotificationRecipient> recipients = StrictJson.texts(rule, "recipients", path).stream()
            .map(code -> StrictJson.parseCode(code, path + ".recipients", NotificationRecipient.class)).toList();
        List<NotificationChannel> channels = StrictJson.texts(rule, "channels", path).stream()
            .map(code -> StrictJson.parseCode(code, path + ".channels", NotificationChannel.class)).toList();
        return new NotificationRule(trigger, type, recipients, StrictJson.ids(rule, "userIds", path), channels,
            StrictJson.text(rule, "callbackCode", path));
    }

    /**
     * 解析流程设置。
     *
     * @param settings 设置 JSON；可能为 null
     * @return 流程设置
     */
    private WorkflowSettings readSettings(JsonNode settings) {
        if (settings == null) {
            return WorkflowSettings.EMPTY;
        }
        String path = ROOT + ".settings";
        StrictJson.object(settings, path);
        StrictJson.allowOnly(settings, path, Set.of("notifications"));
        return new WorkflowSettings(StrictJson.array(settings, "notifications", path, this::readRule));
    }

    /**
     * 解析一条连线。
     *
     * @param edge 连线 JSON
     * @param path JSON 路径
     * @return 连线
     */
    private WorkflowEdge readEdge(JsonNode edge, String path) {
        StrictJson.object(edge, path);
        StrictJson.allowOnly(edge, path, EDGE_FIELDS);
        return new WorkflowEdge(StrictJson.text(edge, "key", path), StrictJson.text(edge, "from", path),
            StrictJson.text(edge, "to", path));
    }
}
