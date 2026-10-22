package com.paytech.platform.support.workflow.domainservice.validator;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.paytech.platform.support.workflow.core.common.WorkflowValueFormats;
import com.paytech.platform.support.workflow.core.enums.ActionTemplateStatus;
import com.paytech.platform.support.workflow.core.enums.InputMappingSource;
import com.paytech.platform.support.workflow.core.enums.WorkflowActionType;
import com.paytech.platform.support.workflow.core.enums.WorkflowNodeType;
import com.paytech.platform.support.workflow.core.enums.WorkflowProblemCode;
import com.paytech.platform.support.workflow.domain.action.ActionTemplateReference;
import com.paytech.platform.support.workflow.domain.graph.InputMapping;
import com.paytech.platform.support.workflow.domain.graph.NodeAction;
import com.paytech.platform.support.workflow.domain.graph.NodeApproval;
import com.paytech.platform.support.workflow.domain.graph.WorkflowNode;
import com.paytech.platform.support.workflow.domain.validation.WorkflowPublicationInput;
import com.paytech.platform.support.workflow.domain.variable.VariableDeclaration;

/**
 * 校验任务节点（技术方案 3.4 第 5、6 项与动作上限）：至少一个审批人或动作；审批人与执行人是 ACTIVE 的
 * Operations 账号且审批人不重复；动作 key 全局唯一；引用模板 ACTIVE 且不是 DATA_QUERY；输入映射的参数已在模板声明。
 */
final class TaskNodeRules {

    /** 每个节点的动作上限。 */
    static final int MAX_ACTIONS = 20;

    private final WorkflowPublicationInput input;

    private final ProblemCollector problems;

    private final Set<String> actionKeys = new HashSet<>();

    /**
     * 绑定校验输入。
     *
     * @param input 发布校验输入
     * @param problems 问题收集
     */
    TaskNodeRules(WorkflowPublicationInput input, ProblemCollector problems) {
        this.input = input;
        this.problems = problems;
    }

    /** 校验全部任务节点。 */
    void check() {
        input.graph().nodes().stream().filter(node -> node.type() == WorkflowNodeType.TASK).forEach(this::checkNode);
    }

    /**
     * 校验一个任务节点。
     *
     * @param node 任务节点
     */
    private void checkNode(WorkflowNode node) {
        if (!node.hasApprovers() && !node.hasActions()) {
            problems.node(WorkflowProblemCode.TASK_EMPTY, node.key(), "任务节点至少需要一个审批人或一个动作");
        }
        if (node.actions().size() > MAX_ACTIONS) {
            problems.node(WorkflowProblemCode.TOO_MANY_ACTIONS, node.key(),
                "节点有 " + node.actions().size() + " 个动作，超过上限 " + MAX_ACTIONS + " 个");
        }
        if (node.approval() != null) {
            checkApproval(node, node.approval());
        }
        node.actions().forEach(action -> checkAction(node, action));
    }

    /**
     * 校验审批方式与审批人。
     *
     * @param node 任务节点
     * @param approval 审批配置
     */
    private void checkApproval(WorkflowNode node, NodeApproval approval) {
        if (approval.mode() == null) {
            problems.node(WorkflowProblemCode.APPROVAL_MODE_MISSING, node.key(), "配置审批时必须选择 ANY 或 ALL");
        }
        if (approval.approverIds().isEmpty()) {
            problems.node(WorkflowProblemCode.APPROVER_MISSING, node.key(), "配置审批时至少需要一个审批人");
        }
        Set<Long> seen = new HashSet<>();
        for (Long approverId : approval.approverIds()) {
            if (!seen.add(approverId)) {
                problems.item(WorkflowProblemCode.APPROVER_DUPLICATE, node.key(), approverId.toString(),
                    "审批人重复：" + approverId);
            } else if (!input.activeOperationsAccountIds().contains(approverId)) {
                problems.item(WorkflowProblemCode.APPROVER_INACTIVE, node.key(), approverId.toString(),
                    "审批人不是 ACTIVE 的 Operations 账号：" + approverId);
            }
        }
    }

    /**
     * 校验一个节点动作。
     *
     * @param node 任务节点
     * @param action 节点动作
     */
    private void checkAction(WorkflowNode node, NodeAction action) {
        checkActionKey(node, action.key());
        if (action.executorId() == null) {
            problems.item(WorkflowProblemCode.EXECUTOR_MISSING, node.key(), action.key(), "动作必须指定默认执行人");
        } else if (!input.activeOperationsAccountIds().contains(action.executorId())) {
            problems.item(WorkflowProblemCode.EXECUTOR_INACTIVE, node.key(), action.key(),
                "执行人不是 ACTIVE 的 Operations 账号：" + action.executorId());
        }
        ActionTemplateReference template = checkTemplate(node, action);
        if (template != null) {
            checkInputs(node, action, template);
        }
    }

    /**
     * 校验动作 key 格式、保留前缀与全局唯一。
     *
     * @param node 任务节点
     * @param key 动作 key
     */
    private void checkActionKey(WorkflowNode node, String key) {
        if (!WorkflowValueFormats.isKey(key)) {
            problems.item(WorkflowProblemCode.ACTION_KEY_INVALID, node.key(), key,
                "动作 key 必须以字母开头，只含字母、数字或下划线，最长 64：" + key);
        } else if (VariableDeclaration.START_PREFIX.equals(key)) {
            problems.item(WorkflowProblemCode.ACTION_KEY_RESERVED, node.key(), key, "动作 key 不能是 start，它是启动变量的前缀");
        } else if (!actionKeys.add(key)) {
            problems.item(WorkflowProblemCode.ACTION_KEY_DUPLICATE, node.key(), key, "动作 key 在流程内重复：" + key);
        }
    }

    /**
     * 校验引用模板存在、ACTIVE 且不是预留类型。
     *
     * @param node 任务节点
     * @param action 节点动作
     * @return 可用于输入校验的模板；不存在时为 null
     */
    private ActionTemplateReference checkTemplate(WorkflowNode node, NodeAction action) {
        ActionTemplateReference template = action.templateId() == null ? null
            : input.templates().get(action.templateId());
        if (template == null) {
            problems.item(WorkflowProblemCode.TEMPLATE_NOT_FOUND, node.key(), action.key(),
                "动作引用的模板不存在：" + action.templateId());
            return null;
        }
        if (template.status() != ActionTemplateStatus.ACTIVE) {
            problems.item(WorkflowProblemCode.TEMPLATE_INACTIVE, node.key(), action.key(),
                "动作引用的模板已停用：" + action.templateId());
        }
        if (template.actionType() == WorkflowActionType.DATA_QUERY) {
            problems.item(WorkflowProblemCode.TEMPLATE_RESERVED_TYPE, node.key(), action.key(),
                "数据查询动作本期未开放，不能被发布的流程引用");
        }
        return template;
    }

    /**
     * 校验输入映射的参数已在模板声明、取值完整。变量是否可用由 {@link VariableFlowRules} 校验。
     *
     * @param node 任务节点
     * @param action 节点动作
     * @param template 引用模板
     */
    private void checkInputs(WorkflowNode node, NodeAction action, ActionTemplateReference template) {
        Set<String> accepted = template.config().inputKeys();
        for (Map.Entry<String, InputMapping> entry : action.inputs().entrySet()) {
            if (!accepted.contains(entry.getKey())) {
                problems.item(WorkflowProblemCode.INPUT_KEY_UNDECLARED, node.key(), action.key(),
                    "动作 " + action.key() + " 的输入 " + entry.getKey() + " 不在模板声明的参数中");
            }
            InputMapping mapping = entry.getValue();
            boolean missing = mapping.source() == InputMappingSource.VARIABLE
                ? WorkflowValueFormats.isBlank(mapping.value()) : mapping.value() == null;
            if (missing) {
                problems.item(WorkflowProblemCode.INPUT_VALUE_INVALID, node.key(), action.key(),
                    "动作 " + action.key() + " 的输入 " + entry.getKey() + " 缺少变量名或常量值");
            }
        }
    }
}
