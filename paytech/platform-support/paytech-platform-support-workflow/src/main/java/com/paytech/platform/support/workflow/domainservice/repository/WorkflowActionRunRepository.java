package com.paytech.platform.support.workflow.domainservice.repository;

import java.time.Instant;
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

    /** 按原版本保存执行人、状态、提交内容与检查状态；版本不符为并发冲突。 */
    void update(WorkflowActionRun row);

    /**
     * 本分片到期待检查的 DATA_QUERY 动作运行（PENDING）。
     *
     * @param shardIndex 分片序号（按 instance_id 取模）
     * @param shardTotal 分片总数
     * @param checkedBefore 最近检查早于此时间的才到期；从未检查的总是到期
     * @param limit 最多条数
     * @return 动作运行，从未检查的在前，其余按最近检查时间先后
     */
    List<WorkflowActionRun> findDueDataQueryChecks(int shardIndex, int shardTotal, Instant checkedBefore, int limit);

    /**
     * 是否有其他实例的动作输出引用了该文件地址。
     *
     * @param fileAddress 文件地址
     * @param excludeInstanceId 不计入的实例；可空
     * @return 有则返回 true
     */
    boolean referencedByOtherInstance(String fileAddress, Long excludeInstanceId);
}
