package com.paytech.platform.support.workflow.domain.graph;

import java.util.List;

/**
 * 流程设置：实例级的通知与回调规则。
 *
 * @param notifications 实例级规则
 */
public record WorkflowSettings(List<NotificationRule> notifications) {

    /** 没有任何实例级规则的设置。 */
    public static final WorkflowSettings EMPTY = new WorkflowSettings(List.of());

    /** 复制列表，保持不可变。 */
    public WorkflowSettings {
        notifications = List.copyOf(notifications);
    }
}
