package com.paytech.platform.support.workflow.domain.runtime;

import java.util.List;

import com.paytech.platform.support.workflow.core.enums.RejectionKind;

/**
 * 当前节点运行能否驳回、驳回到哪里；页面据此显示或置灰“驳回”，并在汇合后的第一个节点让审批人选择分支。
 *
 * @param kind 处理方式
 * @param targetNodeKeys PREVIOUS_TASK 时为唯一的上一个 TASK 节点；CHOOSE_BRANCH 时为各分支最后一个 TASK 节点，
 *     作为 reject_target_node_key 的候选；不能驳回时为空列表
 */
public record RejectOption(RejectionKind kind, List<String> targetNodeKeys) {

    /** 复制列表，保持不可变。 */
    public RejectOption {
        targetNodeKeys = List.copyOf(targetNodeKeys);
    }

    /**
     * 是否允许驳回。
     *
     * @return 允许返回 true
     */
    public boolean allowed() {
        return kind == RejectionKind.PREVIOUS_TASK || kind == RejectionKind.CHOOSE_BRANCH;
    }
}
