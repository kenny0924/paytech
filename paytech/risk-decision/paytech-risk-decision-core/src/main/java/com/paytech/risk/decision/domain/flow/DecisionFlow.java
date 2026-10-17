package com.paytech.risk.decision.domain.flow;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.paytech.risk.decision.domain.rule.RuleAstValidator.LiteralValue;

/** 决策流配置的模块业务合同；不依赖 HTTP DTO 或图形库，不表示运行结果。 */
public final class DecisionFlow {
    private DecisionFlow() { }

    /** 完整保存命令；两个基准字段同时为空表示首次创建。 */
    public record Save(String pointCode, String flowCode, String flowName, Long baseFlowVersionId,
        Long expectedVersion, String changeReason, Graph graph) { }

    /** 每个版本的完整配置图，节点和连线以稳定键关联。 */
    public record Graph(int schemaVersion, List<Node> nodes, List<Edge> edges) {
        public Graph { nodes = List.copyOf(nodes); edges = List.copyOf(edges); }
    }

    /** 节点配置；布局坐标为整数像素，终态与附加输出只适用于 END，输出按编码排序。 */
    public record Node(String nodeKey, String type, String name, int x, int y, Long ruleSetVersionId,
        String decisionCode, String reasonCode, String reasonMessage, Map<String, String> outputs) {
        public Node {
            outputs = outputs == null ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(outputs));
        }

        /** 不配置附加输出的节点。 */
        public Node(String nodeKey, String type, String name, int x, int y, Long ruleSetVersionId,
            String decisionCode, String reasonCode, String reasonMessage) {
            this(nodeKey, type, name, x, y, ruleSetVersionId, decisionCode, reasonCode, reasonMessage, Map.of());
        }
    }

    /** 同源条件线按 priority 排序；ELSE/无条件为零且 condition 为空。 */
    public record Edge(String edgeKey, String sourceNodeKey, String targetNodeKey, String type,
        int priority, String name, Condition condition) { }

    /** 条件数据格式与递归根。 */
    public record Condition(int schemaVersion, Expression root) { }

    public sealed interface Expression permits Group, Predicate { }

    /** AND/OR 至少两个子项；NOT 恰好一个，原顺序保留。 */
    public record Group(String operator, List<Expression> children) implements Expression {
        public Group { children = List.copyOf(children); }
    }

    /** 字段来源只能是当前点输入或当前出线节点 matched；值为类型明确的字面量。 */
    public record Predicate(String sourceType, String fieldCode, String operator, List<LiteralValue> values,
        List<com.paytech.risk.decision.domain.variable.VariableOperand> operands) implements Expression {
        /** 旧 schemaVersion=1 保留原来源及字面量，不改变旧 Hash 投影。 */
        public Predicate(String sourceType, String fieldCode, String operator, List<LiteralValue> values) {
            this(sourceType, fieldCode, operator, values, null);
        }
        /** 只复制所属格式的集合，禁止调用方修改历史结构。 */
        public Predicate {
            values = values == null ? null : List.copyOf(values);
            operands = operands == null ? null : List.copyOf(operands);
        }
    }

    /** 静态问题坐标，未关联的字段为空；最多保留 100 项。 */
    public record Issue(String code, String nodeKey, String edgeKey, String field, String message) { }

    /** 纯配置校验报告；没有运行命中或执行结果。 */
    public record Validation(boolean valid, List<Issue> issues, boolean truncated) {
        public Validation { issues = List.copyOf(issues); }
    }

    /** 规范化保存输入及其静态报告。 */
    public record Checked(Save normalized, Validation validation) { }

    /** 每个规则集节点的准确引用与当前管理资格。 */
    public record Reference(String nodeKey, long ruleSetId, String ruleSetCode, String ruleSetName,
        long ruleSetVersionId, int versionNo, int ruleCount, String ruleSetStatus, String versionStatus) { }

    /** 本次成功保存的结果，版本取本次提交事实。 */
    public record Saved(long flowId, long flowVersionId, int versionNo, long version, long savedBy,
        Instant savedTime) { }

    /** 整体删除的不可变事实。 */
    public record Deleted(long flowId, long version, long deletedBy, Instant deletedTime) { }
}
