package com.paytech.platform.support.workflow.domainservice.validator;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.paytech.platform.support.workflow.core.common.WorkflowValueFormats;
import com.paytech.platform.support.workflow.core.enums.ConditionOperator;
import com.paytech.platform.support.workflow.core.enums.InputMappingSource;
import com.paytech.platform.support.workflow.core.enums.WorkflowNodeType;
import com.paytech.platform.support.workflow.core.enums.WorkflowProblemCode;
import com.paytech.platform.support.workflow.domain.action.ActionTemplateReference;
import com.paytech.platform.support.workflow.domain.graph.ConditionClause;
import com.paytech.platform.support.workflow.domain.graph.ConditionValue;
import com.paytech.platform.support.workflow.domain.graph.EdgeCondition;
import com.paytech.platform.support.workflow.domain.graph.NodeAction;
import com.paytech.platform.support.workflow.domain.graph.WorkflowEdge;
import com.paytech.platform.support.workflow.domain.graph.WorkflowNode;
import com.paytech.platform.support.workflow.domain.validation.WorkflowPublicationInput;
import com.paytech.platform.support.workflow.domain.variable.VariableDeclaration;

/**
 * 校验条件与输入映射引用的变量（技术方案 3.4 第 7 项）。只在无回环时执行。
 *
 * <p>变量在某节点“可用”指：它来自启动表单，或来自每条到达路径上都必经的上游 TASK 动作输出。
 * 计算按拓扑序进行：普通节点取各前驱输出的交集，并行汇合取并集（全部分支都已完成）；因此并行分支之间的输出互不可见，
 * 汇合后才可用，排他分支各自的输出在分支合并处不可用。</p>
 */
final class VariableFlowRules {

    private final WorkflowPublicationInput input;

    private final GraphTopology topology;

    private final ProblemCollector problems;

    private final Set<String> unresolvedPrefixes = new HashSet<>();

    /**
     * 绑定校验输入。
     *
     * @param input 发布校验输入
     * @param topology 拓扑视图
     * @param problems 问题收集
     */
    VariableFlowRules(WorkflowPublicationInput input, GraphTopology topology, ProblemCollector problems) {
        this.input = input;
        this.topology = topology;
        this.problems = problems;
    }

    /** 计算各节点可用变量并校验引用。 */
    void check() {
        Map<String, Map<String, VariableDeclaration>> available = availability();
        for (WorkflowNode node : input.graph().nodes()) {
            Map<String, VariableDeclaration> variables = available.get(node.key());
            if (variables == null) {
                continue;
            }
            if (node.type() == WorkflowNodeType.EXCLUSIVE) {
                node.conditions().forEach(condition -> checkCondition(node, condition, variables));
            }
            if (node.type() == WorkflowNodeType.TASK) {
                node.actions().forEach(action -> checkInputs(node, action, variables));
            }
        }
    }

    /**
     * 按拓扑序计算每个可达节点进入时可用的变量。
     *
     * @return 节点 key 到可用变量；不可达节点不在结果中
     */
    private Map<String, Map<String, VariableDeclaration>> availability() {
        Map<String, VariableDeclaration> startVariables = new LinkedHashMap<>();
        VariableDeclaration.ofForm(VariableDeclaration.START_PREFIX, input.startForm())
            .forEach(variable -> startVariables.putIfAbsent(variable.name(), variable));
        Map<String, Map<String, VariableDeclaration>> entering = new HashMap<>();
        Map<String, Map<String, VariableDeclaration>> leaving = new HashMap<>();
        for (String key : topology.topologicalOrder()) {
            WorkflowNode node = input.graph().node(key).orElseThrow();
            List<Map<String, VariableDeclaration>> predecessors = topology.validIncoming(key).stream()
                .map(WorkflowEdge::from).filter(leaving::containsKey).map(leaving::get).toList();
            Map<String, VariableDeclaration> variables = node.type() == WorkflowNodeType.START
                ? startVariables : merge(node, predecessors);
            if (variables == null) {
                continue;
            }
            entering.put(key, variables);
            leaving.put(key, withOutputs(node, variables));
        }
        return entering;
    }

    /**
     * 合并前驱输出：并行汇合取并集，其余节点取交集。
     *
     * @param node 当前节点
     * @param predecessors 可达前驱的输出
     * @return 合并结果；没有可达前驱时为 null
     */
    private Map<String, VariableDeclaration> merge(WorkflowNode node,
        List<Map<String, VariableDeclaration>> predecessors) {
        if (predecessors.isEmpty()) {
            return null;
        }
        Map<String, VariableDeclaration> result = new LinkedHashMap<>(predecessors.get(0));
        for (Map<String, VariableDeclaration> other : predecessors.subList(1, predecessors.size())) {
            if (node.type() == WorkflowNodeType.PARALLEL_JOIN) {
                other.forEach(result::putIfAbsent);
            } else {
                result.keySet().retainAll(other.keySet());
            }
        }
        return result;
    }

    /**
     * 节点离开时的变量：TASK 追加其动作的输出；模板缺失的动作记为未解析前缀，避免重复报错。
     *
     * @param node 当前节点
     * @param variables 进入时的变量
     * @return 离开时的变量
     */
    private Map<String, VariableDeclaration> withOutputs(WorkflowNode node, Map<String, VariableDeclaration> variables) {
        if (node.type() != WorkflowNodeType.TASK) {
            return variables;
        }
        Map<String, VariableDeclaration> result = new LinkedHashMap<>(variables);
        for (NodeAction action : node.actions()) {
            ActionTemplateReference template = action.templateId() == null ? null
                : input.templates().get(action.templateId());
            if (template == null || action.key() == null) {
                unresolvedPrefixes.add(action.key());
                continue;
            }
            template.config().outputs(action.key()).forEach(output -> result.putIfAbsent(output.name(), output));
        }
        return result;
    }

    /**
     * 校验一组条件的全部子句。
     *
     * @param node 排他网关
     * @param condition 条件组
     * @param variables 网关处可用的变量
     */
    private void checkCondition(WorkflowNode node, EdgeCondition condition,
        Map<String, VariableDeclaration> variables) {
        for (ConditionClause clause : condition.all()) {
            VariableDeclaration variable = resolve(clause.variable(), variables);
            if (variable == null) {
                reportUnavailable(node, condition.edgeKey(), null, clause.variable());
                continue;
            }
            if (!clause.operator().supports(variable.type())) {
                problems.edge(WorkflowProblemCode.OPERATOR_TYPE_MISMATCH, node.key(), condition.edgeKey(),
                    "比较符 " + clause.operator().getCode() + " 不能用于 " + variable.type().getCode() + " 类型的变量 "
                        + variable.name());
                continue;
            }
            String invalid = ConditionValueRules.problem(clause.operator(), clause.value(), variable);
            if (invalid != null) {
                problems.edge(WorkflowProblemCode.CONDITION_VALUE_INVALID, node.key(), condition.edgeKey(),
                    "变量 " + variable.name() + " 的比较值不合法：" + invalid);
            }
        }
    }

    /**
     * 校验动作的变量型输入映射在本节点可用。
     *
     * @param node 任务节点
     * @param action 节点动作
     * @param variables 节点处可用的变量（不含本节点其他动作的输出）
     */
    private void checkInputs(WorkflowNode node, NodeAction action, Map<String, VariableDeclaration> variables) {
        action.inputs().values().stream()
            .filter(mapping -> mapping.source() == InputMappingSource.VARIABLE)
            .filter(mapping -> !WorkflowValueFormats.isBlank(mapping.value()))
            .filter(mapping -> resolve(mapping.value(), variables) == null)
            .forEach(mapping -> reportUnavailable(node, null, action.key(), mapping.value()));
    }

    /**
     * 按名称查找可用变量。
     *
     * @param name 变量名
     * @param variables 可用变量
     * @return 变量；不可用时为 null
     */
    private VariableDeclaration resolve(String name, Map<String, VariableDeclaration> variables) {
        return name == null ? null : variables.get(name);
    }

    /**
     * 报告变量不可用；引用模板缺失的动作输出已由模板校验报告，不再重复。
     *
     * @param node 节点
     * @param edgeKey 条件所在出边；输入映射时为 null
     * @param actionKey 输入映射所在动作；条件时为 null
     * @param name 变量名
     */
    private void reportUnavailable(WorkflowNode node, String edgeKey, String actionKey, String name) {
        String prefix = name == null || name.indexOf('.') < 0 ? name : name.substring(0, name.indexOf('.'));
        if (prefix != null && unresolvedPrefixes.contains(prefix)) {
            return;
        }
        String message = "变量 " + name + " 在节点 " + node.key()
            + " 不可用：须来自启动表单，或来自每条到达路径上必经的上游动作输出（并行分支之间不可互相引用）";
        if (edgeKey != null) {
            problems.edge(WorkflowProblemCode.VARIABLE_UNAVAILABLE, node.key(), edgeKey, message);
        } else {
            problems.item(WorkflowProblemCode.VARIABLE_UNAVAILABLE, node.key(), actionKey, message);
        }
    }

    /**
     * 比较值形态与格式的校验，供条件子句使用。
     */
    static final class ConditionValueRules {

        private ConditionValueRules() {
        }

        /**
         * 返回比较值的问题说明。
         *
         * @param operator 比较符
         * @param value 比较值
         * @param variable 变量声明
         * @return 问题说明；合法时为 null
         */
        static String problem(ConditionOperator operator, ConditionValue value, VariableDeclaration variable) {
            switch (operator.getValueShape()) {
                case NONE -> {
                    return value == null ? null : operator.getCode() + " 不带比较值";
                }
                case SCALAR -> {
                    if (value == null || value.list()) {
                        return operator.getCode() + " 需要一个比较值";
                    }
                }
                default -> {
                    if (value == null || !value.list() || value.values().isEmpty()) {
                        return operator.getCode() + " 需要非空的比较值列表";
                    }
                }
            }
            return value.values().stream().filter(item -> !formatMatches(item, variable)).findFirst()
                .map(item -> item + " 不符合 " + variable.type().getCode() + " 类型").orElse(null);
        }

        /**
         * 比较值文本是否符合变量类型。
         *
         * @param item 比较值文本
         * @param variable 变量声明
         * @return 符合返回 true
         */
        private static boolean formatMatches(String item, VariableDeclaration variable) {
            return switch (variable.type()) {
                case NUMBER -> WorkflowValueFormats.isDecimal(item);
                case BOOLEAN -> WorkflowValueFormats.isBoolean(item);
                case DATE -> WorkflowValueFormats.isDate(item);
                case ENUM -> variable.enumValues().contains(item);
                default -> true;
            };
        }
    }
}
