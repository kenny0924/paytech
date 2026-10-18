package com.paytech.platform.support.integration.domain.execution;

import java.util.*;

import com.paytech.platform.support.api.enums.ExecuteStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;
import tools.jackson.databind.JsonNode;

/**
 * 已通过运行前校验的流程（流程结构第 1～4 节）：节点、出口按 ID 索引，连线按起点分组并按优先级排序，兜底在最后。
 *
 * <p>
 * 只从已校验的 JSON 构建，不重复结构校验。
 */
@Getter
@AllArgsConstructor
public class ExecutionFlow {

    /** 整个流程（初始化 + 全部节点）的总预算。 */
    private final long timeoutMs;

    /** 初始化项，保持配置顺序；执行顺序由依赖决定。 */
    private final List<Initializer> initializers;

    /** 第一个执行的节点 ID。 */
    private final String entry;

    /** 节点，按 ID 索引。 */
    private final Map<String, Node> nodes;

    /** 出口，按 ID 索引。 */
    private final Map<String, Return> returns;

    /** 每个节点的出边：条件连线按 priority 升序，兜底在最后。 */
    private final Map<String, List<Edge>> edges;

    /** 从已校验的 flow_definition_json 构建。 */
    public static ExecutionFlow from(JsonNode flow) {
        var initializers = new ArrayList<Initializer>();
        flow.path("initializers").forEach(item -> initializers.add(new Initializer(item.path("id").asString(),
            item.path("function").asString(), item.path("args"), item.path("required").asBoolean())));
        var nodes = new LinkedHashMap<String, Node>();
        flow.path("nodes").forEach(item -> nodes.put(item.path("id").asString(), new Node(item.path("id").asString(),
            item.path("integrationApi").asString(), item.path("request"), item.path("params"))));
        var returns = new LinkedHashMap<String, Return>();
        flow.path("returns")
            .forEach(item -> returns.put(item.path("id").asString(), new Return(item.path("id").asString(),
                ExecuteStatus.fromCode(item.path("executeStatus").asString()), item.path("outputs"))));
        var edges = new LinkedHashMap<String, List<Edge>>();
        flow.path("edges")
            .forEach(item -> edges.computeIfAbsent(item.path("from").asString(), key -> new ArrayList<>())
                .add(new Edge(item.path("to").asString(),
                    item.path("default").asBoolean(false) ? null : item.path("condition").asString(),
                    item.path("priority").asLong(Long.MAX_VALUE))));
        edges.values().forEach(list -> list.sort(
            Comparator.comparing((Edge edge) -> edge.getCondition() == null).thenComparingLong(Edge::getPriority)));
        return new ExecutionFlow(flow.path("timeoutMs").asLong(), List.copyOf(initializers),
            flow.path("entry").asString(), Map.copyOf(nodes), Map.copyOf(returns), Map.copyOf(edges));
    }

    /** 初始化项：调用注册的初始化函数，结果写入 resources.&lt;id&gt;。 */
    @Getter
    @AllArgsConstructor
    public static class Initializer {

        /** 初始化项 ID。 */
        private final String id;

        /** 注册的初始化函数编码。 */
        private final String function;

        /** 参数取值绑定，键为参数名。 */
        private final JsonNode args;

        /** 失败是否结束调用。 */
        private final boolean required;
    }

    /** 原子 API 节点。 */
    @Getter
    @AllArgsConstructor
    public static class Node {

        /** 节点 ID。 */
        private final String id;

        /** 同一接入方下的原子 API 编码。 */
        private final String integrationApi;

        /** 请求字段取值，键为 &lt;LOCATION&gt;:&lt;path&gt;；缺省为 MissingNode。 */
        private final JsonNode request;

        /** 调用参数取值，键为参数名；缺省为 MissingNode。 */
        private final JsonNode params;
    }

    /** 返回出口。 */
    @Getter
    @AllArgsConstructor
    public static class Return {

        /** 出口 ID。 */
        private final String id;

        /** 出口固定的执行状态。 */
        private final ExecuteStatus executeStatus;

        /** 标准 API 输出取值，键为响应字段路径；ERROR 出口为 MissingNode。 */
        private final JsonNode outputs;
    }

    /** 一条出边。 */
    @Getter
    @AllArgsConstructor
    public static class Edge {

        /** 目标节点或出口 ID。 */
        private final String to;

        /** 布尔条件；兜底连线为 null。 */
        private final String condition;

        /** 判断顺序；兜底连线为 Long.MAX_VALUE。 */
        private final long priority;
    }
}
