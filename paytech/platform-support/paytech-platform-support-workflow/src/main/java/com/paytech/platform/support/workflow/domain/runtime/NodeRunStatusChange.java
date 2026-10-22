package com.paytech.platform.support.workflow.domain.runtime;

import com.paytech.platform.support.workflow.core.enums.NodeRunStatus;

/**
 * 已有节点运行的一次状态变化；调用方以 fromStatus 作为条件更新的期望旧状态。
 *
 * @param nodeRunId 节点运行 ID
 * @param nodeKey 节点 key
 * @param fromStatus 期望的旧状态
 * @param toStatus 新状态
 */
public record NodeRunStatusChange(Long nodeRunId, String nodeKey, NodeRunStatus fromStatus, NodeRunStatus toStatus) {
}
