package com.paytech.platform.support.workflow.domainservice.validator;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.paytech.platform.support.workflow.core.common.WorkflowValueFormats;
import com.paytech.platform.support.workflow.core.enums.NotificationRecipient;
import com.paytech.platform.support.workflow.core.enums.NotificationRuleType;
import com.paytech.platform.support.workflow.core.enums.NotificationTrigger;
import com.paytech.platform.support.workflow.core.enums.WorkflowNodeType;
import com.paytech.platform.support.workflow.core.enums.WorkflowProblemCode;
import com.paytech.platform.support.workflow.domain.graph.NotificationRule;
import com.paytech.platform.support.workflow.domain.graph.WorkflowNode;
import com.paytech.platform.support.workflow.domain.validation.WorkflowPublicationInput;

/**
 * 校验节点级与实例级的通知、回调规则（技术方案 4.2）：触发事件与位置相符，NOTIFY 有对象与渠道，
 * CALLBACK 有合法回调编码，指定用户是 ACTIVE 的 Operations 账号。
 */
final class NotificationRules {

    private static final Set<NotificationTrigger> NEED_APPROVERS = EnumSet.of(NotificationTrigger.APPROVAL_OPENED,
        NotificationTrigger.RETURNED_FOR_REVISION, NotificationTrigger.REJECTED);

    private static final Set<NotificationTrigger> NEED_ACTIONS = EnumSet.of(NotificationTrigger.VERIFICATION_FAILED,
        NotificationTrigger.RETURNED_FOR_REVISION);

    private final WorkflowPublicationInput input;

    private final ProblemCollector problems;

    /**
     * 绑定校验输入。
     *
     * @param input 发布校验输入
     * @param problems 问题收集
     */
    NotificationRules(WorkflowPublicationInput input, ProblemCollector problems) {
        this.input = input;
        this.problems = problems;
    }

    /** 校验全部节点规则与流程设置中的实例级规则。 */
    void check() {
        for (WorkflowNode node : input.graph().nodes()) {
            if (node.notifications().isEmpty()) {
                continue;
            }
            if (node.type() != WorkflowNodeType.TASK) {
                problems.node(WorkflowProblemCode.NOTIFICATION_NOT_ALLOWED, node.key(), "只有任务节点可以配置通知与回调规则");
                continue;
            }
            checkRules(node, node.notifications());
        }
        checkRules(null, input.graph().settings().notifications());
    }

    /**
     * 校验一组规则。
     *
     * @param node 所在任务节点；流程设置时为 null
     * @param rules 规则
     */
    private void checkRules(WorkflowNode node, List<NotificationRule> rules) {
        for (int index = 0; index < rules.size(); index++) {
            NotificationRule rule = rules.get(index);
            String item = "notifications[" + index + "]";
            checkTrigger(node, rule, item);
            if (rule.type() == NotificationRuleType.NOTIFY) {
                checkNotify(node, rule, item);
            } else {
                checkCallback(node, rule, item);
            }
        }
    }

    /**
     * 校验触发事件与规则位置、节点配置相符。
     *
     * @param node 所在任务节点；流程设置时为 null
     * @param rule 规则
     * @param item 规则序号
     */
    private void checkTrigger(WorkflowNode node, NotificationRule rule, String item) {
        NotificationTrigger trigger = rule.trigger();
        String nodeKey = node == null ? null : node.key();
        if (trigger.isInstanceLevel() != (node == null)) {
            problems.item(WorkflowProblemCode.NOTIFICATION_TRIGGER_INVALID, nodeKey, item,
                "触发事件 " + trigger.getCode() + (node == null ? " 是节点级的，不能配置在流程设置中" : " 是实例级的，只能配置在流程设置中"));
            return;
        }
        boolean missingApprovers = node != null && NEED_APPROVERS.contains(trigger) && !node.hasApprovers();
        boolean missingActions = node != null && NEED_ACTIONS.contains(trigger) && !node.hasActions();
        if (missingApprovers || missingActions) {
            problems.item(WorkflowProblemCode.NOTIFICATION_TRIGGER_INVALID, nodeKey, item,
                "节点没有" + (missingApprovers ? "审批人" : "动作") + "，不会发生 " + trigger.getCode());
        }
    }

    /**
     * 校验通知人员规则。
     *
     * @param node 所在任务节点；流程设置时为 null
     * @param rule 规则
     * @param item 规则序号
     */
    private void checkNotify(WorkflowNode node, NotificationRule rule, String item) {
        String nodeKey = node == null ? null : node.key();
        if (rule.callbackCode() != null) {
            problems.item(WorkflowProblemCode.NOTIFICATION_RULE_INVALID, nodeKey, item, "通知规则不能填写回调编码");
        }
        if (rule.recipients().isEmpty()) {
            problems.item(WorkflowProblemCode.NOTIFICATION_RECIPIENT_MISSING, nodeKey, item, "通知规则至少需要一个通知对象");
        }
        if (rule.channels().isEmpty()) {
            problems.item(WorkflowProblemCode.NOTIFICATION_CHANNEL_MISSING, nodeKey, item, "通知规则至少需要一个通知渠道");
        }
        rule.recipients().forEach(recipient -> checkRecipient(node, recipient, item));
        checkSpecifiedUsers(nodeKey, rule, item);
    }

    /**
     * 校验通知对象在规则位置上有可通知的人。
     *
     * @param node 所在任务节点；流程设置时为 null
     * @param recipient 通知对象
     * @param item 规则序号
     */
    private void checkRecipient(WorkflowNode node, NotificationRecipient recipient, String item) {
        boolean invalid = switch (recipient) {
            case NODE_EXECUTORS -> node == null || !node.hasActions();
            case NODE_APPROVERS -> node == null || !node.hasApprovers();
            default -> false;
        };
        if (invalid) {
            problems.item(WorkflowProblemCode.NOTIFICATION_RECIPIENT_INVALID, node == null ? null : node.key(), item,
                "通知对象 " + recipient.getCode() + " 在此没有可通知的人");
        }
    }

    /**
     * 校验指定用户与 SPECIFIED_USERS 成对出现，且都是 ACTIVE 的 Operations 账号。
     *
     * @param nodeKey 所在节点 key；流程设置时为 null
     * @param rule 规则
     * @param item 规则序号
     */
    private void checkSpecifiedUsers(String nodeKey, NotificationRule rule, String item) {
        boolean specified = rule.recipients().contains(NotificationRecipient.SPECIFIED_USERS);
        if (specified && rule.userIds().isEmpty()) {
            problems.item(WorkflowProblemCode.NOTIFICATION_RECIPIENT_MISSING, nodeKey, item, "指定用户时必须填写 userIds");
        }
        if (!specified && !rule.userIds().isEmpty()) {
            problems.item(WorkflowProblemCode.NOTIFICATION_RULE_INVALID, nodeKey, item,
                "填写 userIds 时通知对象必须包含 SPECIFIED_USERS");
        }
        rule.userIds().stream().filter(userId -> !input.activeOperationsAccountIds().contains(userId))
            .forEach(userId -> problems.item(WorkflowProblemCode.NOTIFICATION_RECIPIENT_INACTIVE, nodeKey, item,
                "指定的通知用户不是 ACTIVE 的 Operations 账号：" + userId));
    }

    /**
     * 校验业务回调规则。
     *
     * @param node 所在任务节点；流程设置时为 null
     * @param rule 规则
     * @param item 规则序号
     */
    private void checkCallback(WorkflowNode node, NotificationRule rule, String item) {
        String nodeKey = node == null ? null : node.key();
        if (!WorkflowValueFormats.isCallbackCode(rule.callbackCode())) {
            problems.item(WorkflowProblemCode.CALLBACK_CODE_INVALID, nodeKey, item,
                "回调编码必须以大写字母开头，只含大写字母、数字或下划线，最长 64：" + rule.callbackCode());
        }
        if (!rule.recipients().isEmpty() || !rule.userIds().isEmpty() || !rule.channels().isEmpty()) {
            problems.item(WorkflowProblemCode.NOTIFICATION_RULE_INVALID, nodeKey, item, "回调规则不能填写通知对象与渠道");
        }
    }
}
