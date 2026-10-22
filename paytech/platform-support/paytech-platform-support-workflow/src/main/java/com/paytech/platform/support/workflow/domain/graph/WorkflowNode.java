package com.paytech.platform.support.workflow.domain.graph;

import java.util.List;

import com.paytech.platform.support.workflow.core.enums.WorkflowNodeType;

/**
 * 流程图节点。审批与动作只属于 TASK，条件与默认边只属于 EXCLUSIVE，其余类型出现时由发布校验报告。
 *
 * @param key 节点 key，版本内唯一
 * @param type 节点类型
 * @param name 显示名称；可能为空
 * @param position 画布位置；可能为空
 * @param approval 审批配置；没有审批时为 null
 * @param actions 动作，按配置顺序
 * @param conditions 排他网关的条件组，按判断优先级排列
 * @param defaultEdgeKey 排他网关的默认出边；可能为空
 * @param notifications 节点级通知与回调规则
 */
public record WorkflowNode(String key, WorkflowNodeType type, String name, NodePosition position,
    NodeApproval approval, List<NodeAction> actions, List<EdgeCondition> conditions, String defaultEdgeKey,
    List<NotificationRule> notifications) {

    /** 复制列表，保持不可变。 */
    public WorkflowNode {
        actions = List.copyOf(actions);
        conditions = List.copyOf(conditions);
        notifications = List.copyOf(notifications);
    }

    /**
     * 是否配置了至少一个审批人。
     *
     * @return 有审批人返回 true
     */
    public boolean hasApprovers() {
        return approval != null && !approval.approverIds().isEmpty();
    }

    /**
     * 是否配置了动作；退回修改只对有动作的节点开放。
     *
     * @return 有动作返回 true
     */
    public boolean hasActions() {
        return !actions.isEmpty();
    }
}
