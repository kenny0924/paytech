package com.paytech.platform.support.workflow.domainservice.process;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.LongSupplier;

import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowActionRun;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowApproval;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowCallback;
import com.paytech.platform.support.workflow.core.enums.CallbackStatus;
import com.paytech.platform.support.workflow.core.enums.NotificationChannel;
import com.paytech.platform.support.workflow.core.enums.NotificationRecipient;
import com.paytech.platform.support.workflow.core.enums.NotificationRuleType;
import com.paytech.platform.support.workflow.domain.WorkflowInstanceContext;
import com.paytech.platform.support.workflow.domain.graph.NotificationRule;
import com.paytech.platform.support.workflow.domain.runtime.CallbackTarget;
import com.paytech.platform.support.workflow.domainservice.convert.WorkflowRuntimeJson;

/**
 * 把状态事件按版本冻结的规则转成通知回调记录（技术方案 4.2）。
 *
 * <p>节点级事件匹配该节点的 notifications，实例级事件匹配流程设置。NOTIFY 只为 EMAIL 渠道登记投递（我的待办是查询结果，
 * 不另投递），收件人按事件发生后的当前人员解析，去重后为空则不登记；CALLBACK 冻结发生时的状态变化、操作人、意见与实例版本。</p>
 */
final class CallbackPlanner {

    private final WorkflowRuntimeJson json = new WorkflowRuntimeJson();

    /**
     * 为本次操作的全部事件登记通知回调。
     *
     * @param context 运行上下文
     * @param events 事件
     * @param ids ID 生成器
     */
    void plan(WorkflowInstanceContext context, List<RuntimeEvent> events, LongSupplier ids) {
        long instanceVersion = context.getChange().isNewInstance() ? 1 : context.getInstance().getVersion() + 1;
        for (RuntimeEvent event : events) {
            List<NotificationRule> rules = event.nodeRun() == null ? context.getGraph().settings().notifications()
                : context.node(event.nodeRun().getNodeKey()).notifications();
            rules.stream().filter(rule -> rule.trigger() == event.trigger())
                .forEach(rule -> register(context, event, rule, target(context, event, rule, instanceVersion), ids));
        }
    }

    /**
     * 规则的投递目标；NOTIFY 没有需投递的渠道或收件人时为 null。
     *
     * @param context 运行上下文
     * @param event 事件
     * @param rule 规则
     * @param instanceVersion 变化后的实例版本
     * @return 目标或 null
     */
    CallbackTarget target(WorkflowInstanceContext context, RuntimeEvent event, NotificationRule rule,
        long instanceVersion) {
        if (rule.type() == NotificationRuleType.CALLBACK) {
            return new CallbackTarget(List.of(), List.of(), rule.callbackCode(), event.fromStatus(), event.toStatus(),
                event.actorId(), event.comment(), instanceVersion);
        }
        if (!rule.channels().contains(NotificationChannel.EMAIL)) {
            return null;
        }
        Set<Long> recipients = new LinkedHashSet<>();
        rule.recipients().forEach(recipient -> recipients.addAll(resolve(context, event, rule, recipient)));
        return recipients.isEmpty() ? null : new CallbackTarget(List.copyOf(recipients),
            List.of(NotificationChannel.EMAIL.getCode()), null, event.fromStatus(), event.toStatus(), event.actorId(),
            event.comment(), instanceVersion);
    }

    /**
     * 解析一类通知对象。
     *
     * @param context 运行上下文
     * @param event 事件
     * @param rule 规则
     * @param recipient 通知对象
     * @return 账号
     */
    List<Long> resolve(WorkflowInstanceContext context, RuntimeEvent event, NotificationRule rule,
        NotificationRecipient recipient) {
        return switch (recipient) {
            case STARTER -> List.of(context.getInstance().getStartedBy());
            case SPECIFIED_USERS -> rule.userIds();
            case NODE_EXECUTORS -> event.nodeRun() == null ? List.of()
                : context.actionRunsOf(event.nodeRun().getNodeRunId()).stream().map(WorkflowActionRun::getAssigneeId)
                    .toList();
            case NODE_APPROVERS -> event.nodeRun() == null ? List.of() : approvers(context, event);
        };
    }

    /**
     * 节点审批人：已开放审批时取审批行，否则取即将使用的人选。
     *
     * @param context 运行上下文
     * @param event 事件
     * @return 审批人
     */
    List<Long> approvers(WorkflowInstanceContext context, RuntimeEvent event) {
        List<WorkflowApproval> rows = context.approvalsOf(event.nodeRun().getNodeRunId());
        return rows.isEmpty() ? context.approversFor(event.nodeRun().getNodeKey())
            : rows.stream().map(WorkflowApproval::getApproverId).toList();
    }

    /**
     * 登记一条通知回调。
     *
     * @param context 运行上下文
     * @param event 事件
     * @param rule 规则
     * @param target 目标；为 null 时不登记
     * @param ids ID 生成器
     */
    void register(WorkflowInstanceContext context, RuntimeEvent event, NotificationRule rule, CallbackTarget target,
        LongSupplier ids) {
        if (target == null) {
            return;
        }
        WorkflowCallback row = new WorkflowCallback();
        row.setCallbackId(ids.getAsLong());
        row.setInstanceId(context.getInstance().getInstanceId());
        row.setNodeRunId(event.nodeRun() == null ? null : event.nodeRun().getNodeRunId());
        row.setTriggerCode(event.trigger());
        row.setCallbackType(rule.type());
        row.setTarget(json.writeTarget(target));
        row.setStatus(CallbackStatus.PENDING);
        row.setAttemptCount(0);
        row.setVersion(1);
        row.setCreateTime(context.getNow());
        row.setUpdateTime(context.getNow());
        context.getCallbacks().add(row);
        context.getChange().getNewCallbacks().add(row);
    }
}
