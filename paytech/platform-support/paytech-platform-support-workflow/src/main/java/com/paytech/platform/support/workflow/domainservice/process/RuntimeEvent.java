package com.paytech.platform.support.workflow.domainservice.process;

import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowNodeRun;
import com.paytech.platform.support.workflow.core.enums.NodeRunStatus;
import com.paytech.platform.support.workflow.core.enums.NotificationTrigger;

/**
 * 运行中发生的一次状态事件，用于匹配通知与回调规则。
 *
 * @param trigger 触发事件
 * @param nodeRun 节点级事件的节点运行；实例级为 null
 * @param fromStatus 变化前状态；没有时为 null
 * @param toStatus 变化后状态；没有时为 null
 * @param actorId 触发账号；系统推进时为 null
 * @param comment 意见、说明或原因；没有时为 null
 */
record RuntimeEvent(NotificationTrigger trigger, WorkflowNodeRun nodeRun, String fromStatus, String toStatus,
    Long actorId, String comment) {

    /**
     * 节点级事件。
     *
     * @param trigger 触发事件
     * @param run 节点运行
     * @param from 变化前状态
     * @param to 变化后状态
     * @param actorId 触发账号
     * @param comment 意见
     * @return 事件
     */
    static RuntimeEvent node(NotificationTrigger trigger, WorkflowNodeRun run, NodeRunStatus from, NodeRunStatus to,
        Long actorId, String comment) {
        return new RuntimeEvent(trigger, run, from == null ? null : from.getCode(), to == null ? null : to.getCode(),
            actorId, comment);
    }

    /**
     * 实例级事件。
     *
     * @param trigger 触发事件
     * @param from 变化前状态
     * @param to 变化后状态
     * @param actorId 触发账号
     * @param comment 原因
     * @return 事件
     */
    static RuntimeEvent instance(NotificationTrigger trigger, String from, String to, Long actorId, String comment) {
        return new RuntimeEvent(trigger, null, from, to, actorId, comment);
    }
}
