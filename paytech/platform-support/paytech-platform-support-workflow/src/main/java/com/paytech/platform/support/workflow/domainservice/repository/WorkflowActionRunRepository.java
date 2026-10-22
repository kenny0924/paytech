package com.paytech.platform.support.workflow.domainservice.repository;

import java.util.List;

import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowActionRun;

/** 动作运行（t_workflow_action_run）的单表访问。 */
public interface WorkflowActionRunRepository {

    /** 插入新动作运行。 */
    void insert(WorkflowActionRun row);

    /** 按主键读取；不存在为 null。 */
    WorkflowActionRun find(long actionRunId);

    /** 实例的全部动作运行。 */
    List<WorkflowActionRun> findByInstance(long instanceId);

    /** 按原版本保存执行人、状态与提交内容；版本不符为并发冲突。 */
    void update(WorkflowActionRun row);

    /**
     * 是否有其他实例的动作输出引用了该文件地址。
     *
     * @param fileAddress 文件地址
     * @param excludeInstanceId 不计入的实例；可空
     * @return 有则返回 true
     */
    boolean referencedByOtherInstance(String fileAddress, Long excludeInstanceId);
}
