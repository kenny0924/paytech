package com.paytech.risk.test.builder.decision;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.paytech.risk.decision.domain.enums.DecisionValue;
import com.paytech.risk.decision.domain.enums.FlowEdgeType;
import com.paytech.risk.decision.domain.enums.FlowNodeType;

/**
 * 已保存决策流（管理根 + 不可变版本序列 + 版本图）的测试 Builder
 * <li>版本号从 {@link #firstVersionNo(int)}（默认 1）连续递增，最后一个版本为根的当前配置指针。</li>
 * <li>每个版本携带一张满足严格图规则的完整配置图，读取时可被生产装配器原样重建。</li>
 *
 * @author Kenny
 */
public final class FlowBuilder {
    /** 版本时间基准，UTC 微秒精度。 */
    static final Instant BASE_TIME = Instant.parse("2026-04-05T06:07:08.000001Z");

    /** 整体删除的操作者。 */
    public static final long DELETED_BY = 70009L;

    /** 节点出口条件：本节点命中（NODE_MATCHED EQ true），RFC 8785 规范化后的存储形态。 */
    public static final String MATCHED_CONDITION_JSON = variableCondition("NODE_MATCHED");

    /** 旧 schemaVersion=1 条件：NODE_RESULT matched EQ true，只读历史格式，不可作为新内容保存。 */
    public static final String LEGACY_MATCHED_CONDITION_JSON = "{\"root\":{\"kind\":\"PREDICATE\","
        + "\"operator\":\"EQ\",\"source\":{\"fieldCode\":\"matched\",\"type\":\"NODE_RESULT\"},"
        + "\"values\":[true]},\"schemaVersion\":1}";

    /** 以给定布尔变量 EQ true 的统一变量条件，RFC 8785 规范化后的存储形态。 */
    public static String variableCondition(String variableCode) {
        return "{\"root\":{\"kind\":\"PREDICATE\",\"operands\":["
            + "{\"currencyCode\":null,\"currencyVariableCode\":null,\"fieldCode\":null,\"literalValue\":null,"
            + "\"numericUnit\":null,\"operandRole\":\"LEFT\",\"sourceType\":\"VARIABLE\",\"valueSequence\":1,"
            + "\"valueType\":\"BOOLEAN\",\"variableCode\":\"" + variableCode + "\"},"
            + "{\"currencyCode\":null,\"currencyVariableCode\":null,\"fieldCode\":null,\"literalValue\":true,"
            + "\"numericUnit\":null,\"operandRole\":\"RIGHT\",\"sourceType\":\"LITERAL\",\"valueSequence\":1,"
            + "\"valueType\":\"BOOLEAN\",\"variableCode\":null}],\"operator\":\"EQ\"},\"schemaVersion\":2}";
    }

    /** 一个配置节点；不适用字段为 null，附加输出只用于放行结束节点。 */
    public record NodeSpec(String nodeKey, FlowNodeType type, String name, int x, int y, Long ruleSetVersionId,
        DecisionValue decision, String reasonCode, String reasonMessage, Map<String, String> outputs) {
        public NodeSpec {
            outputs = outputs == null ? Map.of() : Map.copyOf(outputs);
        }

        /** 不配置附加输出的节点。 */
        public NodeSpec(String nodeKey, FlowNodeType type, String name, int x, int y, Long ruleSetVersionId,
            DecisionValue decision, String reasonCode, String reasonMessage) {
            this(nodeKey, type, name, x, y, ruleSetVersionId, decision, reasonCode, reasonMessage, Map.of());
        }
    }

    /** 一条连线；仅 CONDITIONAL 携带规范化条件 JSON。 */
    public record EdgeSpec(String edgeKey, String sourceNodeKey, String targetNodeKey, FlowEdgeType type, int priority,
        String name, String conditionJson) {
    }

    /** 一个版本的完整配置图。 */
    public record GraphSpec(List<NodeSpec> nodes, List<EdgeSpec> edges) {
        public GraphSpec {
            nodes = List.copyOf(nodes);
            edges = List.copyOf(edges);
        }

        /** 开始直接连到给定决定的结束节点。 */
        public static GraphSpec direct(DecisionValue decision) {
            return new GraphSpec(List.of(start(), end("finish", "Finish", decision, null, null)),
                List.of(unconditional("next", "start", "finish", "Next")));
        }

        /** 开始直接连到拒绝结束节点，带原因编码与保存时文案。 */
        public static GraphSpec deny(String reasonCode, String reasonMessage) {
            return new GraphSpec(List.of(start(), end("finish", "Finish", DecisionValue.DENY, reasonCode, reasonMessage)),
                List.of(unconditional("next", "start", "finish", "Next")));
        }

        /** 开始依次经过给定规则集版本节点（rules_1..N），最后到 ALLOW 结束。 */
        public static GraphSpec through(long... ruleSetVersionIds) {
            List<NodeSpec> nodes = new ArrayList<>(List.of(start()));
            List<EdgeSpec> edges = new ArrayList<>();
            String previous = "start";
            for (int i = 0; i < ruleSetVersionIds.length; i++) {
                String key = "rules_" + (i + 1);
                nodes.add(new NodeSpec(key, FlowNodeType.RULE_SET, "Rules " + (i + 1), 120 * (i + 1), 0,
                    ruleSetVersionIds[i], null, null, null));
                edges.add(unconditional("edge_" + (i + 1), previous, key, "Step " + (i + 1)));
                previous = key;
            }
            nodes.add(end("finish", "Finish", DecisionValue.ALLOW, null, null));
            edges.add(unconditional("edge_end", previous, "finish", "Done"));
            return new GraphSpec(nodes, edges);
        }

        /** 开始到规则集节点；命中走条件出口到 REVIEW，其他情况到 ALLOW。 */
        public static GraphSpec branching(long ruleSetVersionId) {
            return branching(ruleSetVersionId, MATCHED_CONDITION_JSON);
        }

        /** 与 {@link #branching(long)} 相同的形态，条件出口使用给定条件 JSON。 */
        public static GraphSpec branching(long ruleSetVersionId, String conditionJson) {
            return new GraphSpec(
                List.of(start(),
                    new NodeSpec("rules", FlowNodeType.RULE_SET, "Rules", 120, 0, ruleSetVersionId, null, null, null),
                    end("review", "Manual review", DecisionValue.REVIEW, null, null),
                    end("finish", "Finish", DecisionValue.ALLOW, null, null)),
                List.of(unconditional("enter", "start", "rules", "Enter"),
                    new EdgeSpec("matched", "rules", "review", FlowEdgeType.CONDITIONAL, 1, "Matched",
                        conditionJson),
                    new EdgeSpec("otherwise", "rules", "finish", FlowEdgeType.ELSE, 0, "Otherwise", null)));
        }

        /** 规则集节点数量。 */
        public int ruleSetNodeCount() {
            return (int) nodes.stream().filter(n -> n.type() == FlowNodeType.RULE_SET).count();
        }

        private static NodeSpec start() {
            return new NodeSpec("start", FlowNodeType.START, "Start", -120, 0, null, null, null, null);
        }

        private static NodeSpec end(String key, String name, DecisionValue decision, String reasonCode,
            String reasonMessage) {
            return new NodeSpec(key, FlowNodeType.END, name, 240, -60, null, decision, reasonCode, reasonMessage);
        }

        private static EdgeSpec unconditional(String key, String source, String target, String name) {
            return new EdgeSpec(key, source, target, FlowEdgeType.UNCONDITIONAL, 0, name, null);
        }
    }

    /** 一个版本的保存事实。 */
    public record VersionSpec(int versionNo, String flowName, String changeReason, long createdBy, Instant createTime,
        GraphSpec graph) {
    }

    private final List<GraphSpec> graphs;

    private int firstVersionNo = 1;

    private String currentName;

    private boolean deleted;

    private Instant updateTime;

    private FlowBuilder(List<GraphSpec> graphs) {
        this.graphs = graphs;
    }

    /** 按给定图依次生成版本，最后一个为当前版本。 */
    public static FlowBuilder versions(GraphSpec... graphs) {
        return new FlowBuilder(List.of(graphs));
    }

    /** 生成给定数量的版本，每个版本均为开始直连 ALLOW 结束。 */
    public static FlowBuilder versions(int count) {
        List<GraphSpec> graphs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            graphs.add(GraphSpec.direct(DecisionValue.ALLOW));
        }
        return new FlowBuilder(graphs);
    }

    /** 首个版本的业务版本号，用于构造版本号上限。 */
    public FlowBuilder firstVersionNo(int versionNo) {
        this.firstVersionNo = versionNo;
        return this;
    }

    /** 覆盖当前版本保存时的流程名称。 */
    public FlowBuilder currentName(String name) {
        this.currentName = name;
        return this;
    }

    /** 流程已整体删除，携带完整删除证据。 */
    public FlowBuilder deleted() {
        this.deleted = true;
        return this;
    }

    /** 指定管理根的最后更新时间，用于列表排序。 */
    public FlowBuilder updateTime(Instant time) {
        this.updateTime = time;
        return this;
    }

    public boolean isDeleted() {
        return deleted;
    }

    /** 全部版本事实，按版本号升序。 */
    public List<VersionSpec> versionSpecs() {
        List<VersionSpec> versions = new ArrayList<>();
        for (int i = 0; i < graphs.size(); i++) {
            int versionNo = firstVersionNo + i;
            String name = i == graphs.size() - 1 && currentName != null ? currentName : "Checkout flow v" + versionNo;
            versions.add(new VersionSpec(versionNo, name, "flow change " + versionNo, 70000L + i + 1,
                BASE_TIME.plusSeconds(3600L * (i + 1)).plusNanos(1000L * (i + 1)), graphs.get(i)));
        }
        return versions;
    }

    /** 删除时间晚于当前版本一小时。 */
    public Instant deletedTime() {
        return deleted ? versionSpecs().getLast().createTime().plusSeconds(3600) : null;
    }

    /** 根最后更新时间：显式指定值，否则为删除时间或当前版本保存时间。 */
    public Instant rootUpdateTime() {
        if (updateTime != null) {
            return updateTime;
        }
        return deleted ? deletedTime() : versionSpecs().getLast().createTime();
    }
}
