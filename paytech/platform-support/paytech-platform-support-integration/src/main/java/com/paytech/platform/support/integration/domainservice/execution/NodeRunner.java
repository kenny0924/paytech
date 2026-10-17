package com.paytech.platform.support.integration.domainservice.execution;

import com.paytech.platform.support.integration.domain.execution.Deadline;
import com.paytech.platform.support.integration.domain.execution.ExecutionContext;
import com.paytech.platform.support.integration.domain.execution.ExecutionFlow;
import com.paytech.platform.support.integration.domain.execution.ExecutionSnapshot;
import com.paytech.platform.support.integration.domain.execution.StepResult;

/** 执行一个原子 API 节点；节点内的任何失败都应返回 ERROR 结果而不是抛出。 */
@FunctionalInterface
public interface NodeRunner {

    /**
     * 执行节点。
     *
     * @param snapshot 本次执行快照
     * @param node 节点
     * @param context 运行上下文（只读使用）
     * @param deadline 流程剩余预算
     * @return 节点结果
     */
    StepResult run(ExecutionSnapshot snapshot, ExecutionFlow.Node node, ExecutionContext context, Deadline deadline);
}
