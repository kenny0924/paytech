package com.paytech.platform.support.workflow.domainservice.service;

import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowActionRun;
import com.paytech.platform.support.workflow.domain.WorkflowRuntimeChange;

/** 运行变化的本地保存；必须在调用方已开启的本地事务中调用。 */
public interface WorkflowRuntimeService {

    /**
     * 保存一次运行操作的全部变化并登记补偿任务；任一条件更新行数不符即并发冲突，整个事务回滚。
     *
     * @param change 运行变化
     */
    void apply(WorkflowRuntimeChange change);

    /**
     * 只保存一次未满足或失败的 DATA_QUERY 检查：按动作版本条件更新检查列，不改实例与节点；版本不符即并发冲突。
     *
     * @param row 已记录检查结果的动作运行
     */
    void recordDataQueryCheck(WorkflowActionRun row);
}
