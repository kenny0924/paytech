package com.paytech.platform.support.workflow.test.builder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

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
import com.paytech.platform.support.workflow.domain.graph.NotificationRule;
import com.paytech.platform.support.workflow.domain.graph.WorkflowEdge;
import com.paytech.platform.support.workflow.domain.graph.WorkflowGraph;
import com.paytech.platform.support.workflow.domain.graph.WorkflowNode;
import com.paytech.platform.support.workflow.domain.graph.WorkflowSettings;

/**
 * 流程图测试构造器：按节点与连线逐个声明，build 时不做任何修正，负例可以直接构造。
 */
public final class WorkflowGraphBuilder {

    private final List<WorkflowNode> nodes = new ArrayList<>();

    private final List<WorkflowEdge> edges = new ArrayList<>();

    private final List<NotificationRule> settingsRules = new ArrayList<>();

    /**
     * 空流程图。
     *
     * @return 构造器
     */
    public static WorkflowGraphBuilder graph() {
        return new WorkflowGraphBuilder();
    }

    /**
     * 最小合法流程：start → approve（审批人甲，ANY）→ end。
     *
     * @return 构造器
     */
    public static WorkflowGraphBuilder template() {
        return graph().start("start").task("approve", task -> task.approvers(ApprovalMode.ANY, WorkflowFixtures.APPROVER_A))
            .end("end").chain("start", "approve", "end");
    }

    /**
     * 增加开始节点。
     *
     * @param key 节点 key
     * @return 构造器
     */
    public WorkflowGraphBuilder start(String key) {
        return node(simple(key, WorkflowNodeType.START));
    }

    /**
     * 增加结束节点。
     *
     * @param key 节点 key
     * @return 构造器
     */
    public WorkflowGraphBuilder end(String key) {
        return node(simple(key, WorkflowNodeType.END));
    }

    /**
     * 增加并行分叉。
     *
     * @param key 节点 key
     * @return 构造器
     */
    public WorkflowGraphBuilder split(String key) {
        return node(simple(key, WorkflowNodeType.PARALLEL_SPLIT));
    }

    /**
     * 增加并行汇合。
     *
     * @param key 节点 key
     * @return 构造器
     */
    public WorkflowGraphBuilder join(String key) {
        return node(simple(key, WorkflowNodeType.PARALLEL_JOIN));
    }

    /**
     * 增加任务节点。
     *
     * @param key 节点 key
     * @param spec 配置审批人、动作与通知
     * @return 构造器
     */
    public WorkflowGraphBuilder task(String key, Consumer<TaskSpec> spec) {
        TaskSpec task = new TaskSpec();
        spec.accept(task);
        return node(new WorkflowNode(key, WorkflowNodeType.TASK, key + " 节点", null, task.approval, task.actions,
            List.of(), null, task.rules));
    }

    /**
     * 增加排他网关。
     *
     * @param key 节点 key
     * @param defaultEdgeKey 默认出边
     * @param conditions 条件组，按优先级
     * @return 构造器
     */
    public WorkflowGraphBuilder exclusive(String key, String defaultEdgeKey, EdgeCondition... conditions) {
        return node(new WorkflowNode(key, WorkflowNodeType.EXCLUSIVE, key + " 网关", null, null, List.of(),
            List.of(conditions), defaultEdgeKey, List.of()));
    }

    /**
     * 增加任意节点（用于负例）。
     *
     * @param node 节点
     * @return 构造器
     */
    public WorkflowGraphBuilder node(WorkflowNode node) {
        nodes.add(node);
        return this;
    }

    /**
     * 增加连线。
     *
     * @param key 连线 key
     * @param from 起点
     * @param to 终点
     * @return 构造器
     */
    public WorkflowGraphBuilder edge(String key, String from, String to) {
        edges.add(new WorkflowEdge(key, from, to));
        return this;
    }

    /**
     * 依次连接节点，连线 key 为 {@code from_to}。
     *
     * @param keys 节点 key
     * @return 构造器
     */
    public WorkflowGraphBuilder chain(String... keys) {
        for (int index = 0; index + 1 < keys.length; index++) {
            edge(keys[index] + "_" + keys[index + 1], keys[index], keys[index + 1]);
        }
        return this;
    }

    /**
     * 增加实例级规则。
     *
     * @param rule 规则
     * @return 构造器
     */
    public WorkflowGraphBuilder settingsRule(NotificationRule rule) {
        settingsRules.add(rule);
        return this;
    }

    /**
     * 构造流程图。
     *
     * @return 流程图
     */
    public WorkflowGraph build() {
        return new WorkflowGraph(nodes, edges, new WorkflowSettings(settingsRules));
    }

    /**
     * 条件组。
     *
     * @param edgeKey 出边
     * @param clauses 子句
     * @return 条件组
     */
    public static EdgeCondition when(String edgeKey, ConditionClause... clauses) {
        return new EdgeCondition(edgeKey, List.of(clauses));
    }

    /**
     * 标量子句。
     *
     * @param variable 变量
     * @param operator 比较符
     * @param value 比较值
     * @return 子句
     */
    public static ConditionClause clause(String variable, ConditionOperator operator, String value) {
        return new ConditionClause(variable, operator, value == null ? null : ConditionValue.scalar(value));
    }

    /**
     * 列表子句。
     *
     * @param variable 变量
     * @param operator 比较符
     * @param values 比较值
     * @return 子句
     */
    public static ConditionClause clauseIn(String variable, ConditionOperator operator, String... values) {
        return new ConditionClause(variable, operator, ConditionValue.listOf(Arrays.asList(values)));
    }

    /**
     * 通知规则。
     *
     * @param trigger 触发事件
     * @param recipients 通知对象
     * @return 规则
     */
    public static NotificationRule notifyRule(NotificationTrigger trigger, NotificationRecipient... recipients) {
        return new NotificationRule(trigger, NotificationRuleType.NOTIFY, List.of(recipients), List.of(),
            List.of(NotificationChannel.INBOX, NotificationChannel.EMAIL), null);
    }

    /**
     * 回调规则。
     *
     * @param trigger 触发事件
     * @param callbackCode 回调编码
     * @return 规则
     */
    public static NotificationRule callback(NotificationTrigger trigger, String callbackCode) {
        return new NotificationRule(trigger, NotificationRuleType.CALLBACK, List.of(), List.of(), List.of(),
            callbackCode);
    }

    /**
     * 没有配置的节点。
     *
     * @param key 节点 key
     * @param type 节点类型
     * @return 节点
     */
    private static WorkflowNode simple(String key, WorkflowNodeType type) {
        return new WorkflowNode(key, type, null, null, null, List.of(), List.of(), null, List.of());
    }

    /** 任务节点的配置。 */
    public static final class TaskSpec {

        private NodeApproval approval;

        private final List<NodeAction> actions = new ArrayList<>();

        private final List<NotificationRule> rules = new ArrayList<>();

        /**
         * 设置审批。
         *
         * @param mode 审批方式
         * @param approverIds 审批人
         * @return 配置
         */
        public TaskSpec approvers(ApprovalMode mode, Long... approverIds) {
            approval = new NodeApproval(mode, List.of(approverIds));
            return this;
        }

        /**
         * 增加必选动作。
         *
         * @param key 动作 key
         * @param templateId 模板
         * @param executorId 执行人
         * @return 配置
         */
        public TaskSpec action(String key, Long templateId, Long executorId) {
            actions.add(new NodeAction(key, templateId, true, executorId, Map.of()));
            return this;
        }

        /**
         * 增加可选动作。
         *
         * @param key 动作 key
         * @param templateId 模板
         * @param executorId 执行人
         * @return 配置
         */
        public TaskSpec optionalAction(String key, Long templateId, Long executorId) {
            actions.add(new NodeAction(key, templateId, false, executorId, Map.of()));
            return this;
        }

        /**
         * 增加带变量输入的必选动作。
         *
         * @param key 动作 key
         * @param templateId 模板
         * @param inputKey 输入参数
         * @param variable 变量名
         * @return 配置
         */
        public TaskSpec actionWithVariableInput(String key, Long templateId, String inputKey, String variable) {
            Map<String, InputMapping> inputs = new LinkedHashMap<>();
            inputs.put(inputKey, new InputMapping(InputMappingSource.VARIABLE, variable));
            actions.add(new NodeAction(key, templateId, true, WorkflowFixtures.EXECUTOR_A, inputs));
            return this;
        }

        /**
         * 增加带任意输入的必选动作。
         *
         * @param key 动作 key
         * @param templateId 模板
         * @param inputs 输入映射
         * @return 配置
         */
        public TaskSpec actionWithInputs(String key, Long templateId, Map<String, InputMapping> inputs) {
            actions.add(new NodeAction(key, templateId, true, WorkflowFixtures.EXECUTOR_A, inputs));
            return this;
        }

        /**
         * 增加节点级规则。
         *
         * @param rule 规则
         * @return 配置
         */
        public TaskSpec rule(NotificationRule rule) {
            rules.add(rule);
            return this;
        }
    }
}
