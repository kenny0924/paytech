package com.paytech.platform.support.workflow.domainservice.service;

import com.paytech.platform.support.workflow.domain.WorkflowRuntimeChange;

/** 运行变化的本地保存；必须在调用方已开启的本地事务中调用。 */
public interface WorkflowRuntimeService {

    /**
     * 保存一次运行操作的全部变化并登记补偿任务；任一条件更新行数不符即并发冲突，整个事务回滚。
     *
     * @param change 运行变化
     */
    void apply(WorkflowRuntimeChange change);
}
