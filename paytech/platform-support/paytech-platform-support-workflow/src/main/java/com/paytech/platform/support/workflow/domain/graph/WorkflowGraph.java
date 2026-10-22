package com.paytech.platform.support.workflow.domain.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 流程版本 graph_json 解析后的流程图，构造时建立按 key 与按端点的索引。
 *
 * <p>索引对重复 key 只保留第一个出现者；重复由发布校验报告，运行时只使用通过校验的已发布图。</p>
 */
public final class WorkflowGraph {

    private final List<WorkflowNode> nodes;

    private final List<WorkflowEdge> edges;

    private final WorkflowSettings settings;

    private final Map<String, WorkflowNode> nodesByKey = new LinkedHashMap<>();

    private final Map<String, WorkflowEdge> edgesByKey = new LinkedHashMap<>();

    private final Map<String, List<WorkflowEdge>> outgoing = new LinkedHashMap<>();

    private final Map<String, List<WorkflowEdge>> incoming = new LinkedHashMap<>();

    /**
     * 以节点、连线与流程设置构造流程图。
     *
     * @param nodes 节点，按配置顺序
     * @param edges 连线，按配置顺序；出边顺序即并行分叉的激活顺序
     * @param settings 流程设置；为 null 时视为没有实例级规则
     */
    public WorkflowGraph(List<WorkflowNode> nodes, List<WorkflowEdge> edges, WorkflowSettings settings) {
        this.nodes = List.copyOf(nodes);
        this.edges = List.copyOf(edges);
        this.settings = settings == null ? WorkflowSettings.EMPTY : settings;
        this.nodes.forEach(node -> nodesByKey.putIfAbsent(node.key(), node));
        for (WorkflowEdge edge : this.edges) {
            edgesByKey.putIfAbsent(edge.key(), edge);
            outgoing.computeIfAbsent(edge.from(), key -> new ArrayList<>()).add(edge);
            incoming.computeIfAbsent(edge.to(), key -> new ArrayList<>()).add(edge);
        }
    }

    /** 全部节点，按配置顺序。 */
    public List<WorkflowNode> nodes() {
        return nodes;
    }

    /** 全部连线，按配置顺序。 */
    public List<WorkflowEdge> edges() {
        return edges;
    }

    /** 流程设置。 */
    public WorkflowSettings settings() {
        return settings;
    }

    /**
     * 按 key 查找节点。
     *
     * @param key 节点 key
     * @return 节点；不存在时为空
     */
    public Optional<WorkflowNode> node(String key) {
        return Optional.ofNullable(nodesByKey.get(key));
    }

    /**
     * 按 key 查找连线。
     *
     * @param key 连线 key
     * @return 连线；不存在时为空
     */
    public Optional<WorkflowEdge> edge(String key) {
        return Optional.ofNullable(edgesByKey.get(key));
    }

    /**
     * 节点的出边，按连线配置顺序。
     *
     * @param nodeKey 节点 key
     * @return 出边；没有时为空列表
     */
    public List<WorkflowEdge> outgoing(String nodeKey) {
        return Collections.unmodifiableList(outgoing.getOrDefault(nodeKey, List.of()));
    }

    /**
     * 节点的入边，按连线配置顺序。
     *
     * @param nodeKey 节点 key
     * @return 入边；没有时为空列表
     */
    public List<WorkflowEdge> incoming(String nodeKey) {
        return Collections.unmodifiableList(incoming.getOrDefault(nodeKey, List.of()));
    }

    /**
     * 流程图引用的动作模板 ID，供调用方在发布校验前查询模板事实。
     *
     * @return 模板 ID，按出现顺序去重
     */
    public Set<Long> referencedTemplateIds() {
        Set<Long> ids = new LinkedHashSet<>();
        nodes.stream().flatMap(node -> node.actions().stream()).map(NodeAction::templateId).filter(Objects::nonNull)
            .forEach(ids::add);
        return ids;
    }

    /**
     * 流程图引用的人员：审批人、动作执行人与通知指定用户，供调用方查询哪些是 ACTIVE Operations 账号。
     *
     * @return 账号 ID，按出现顺序去重
     */
    public Set<Long> referencedAccountIds() {
        Set<Long> ids = new LinkedHashSet<>();
        for (WorkflowNode node : nodes) {
            if (node.approval() != null) {
                ids.addAll(node.approval().approverIds());
            }
            node.actions().stream().map(NodeAction::executorId).filter(Objects::nonNull).forEach(ids::add);
            node.notifications().forEach(rule -> ids.addAll(rule.userIds()));
        }
        settings.notifications().forEach(rule -> ids.addAll(rule.userIds()));
        return ids;
    }
}
