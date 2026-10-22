package com.paytech.platform.support.workflow.domain.graph;

import java.util.List;

import com.paytech.platform.support.workflow.core.enums.ApprovalMode;

/**
 * 任务节点的审批配置；版本中的审批人是默认人选，发起时可修改。
 *
 * @param mode 审批方式；草稿中可能缺失，由发布校验报告
 * @param approverIds 默认审批人 Operations 账号 ID，按配置顺序
 */
public record NodeApproval(ApprovalMode mode, List<Long> approverIds) {

    /** 复制列表，保持不可变。 */
    public NodeApproval {
        approverIds = List.copyOf(approverIds);
    }
}
