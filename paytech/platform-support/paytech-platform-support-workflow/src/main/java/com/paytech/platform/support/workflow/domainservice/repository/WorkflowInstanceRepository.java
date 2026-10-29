package com.paytech.platform.support.workflow.domainservice.repository;

import java.util.List;

import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowInstance;
import com.paytech.platform.support.workflow.domain.query.WorkflowInstanceFilter;

/** 流程实例（t_workflow_instance）的单表访问。 */
public interface WorkflowInstanceRepository {

    /** 插入新实例。 */
    void insert(WorkflowInstance row);

    /** 按主键读取；不存在为 null。 */
    WorkflowInstance find(long instanceId);

    /**
     * 同一定义下业务关联键的 RUNNING 实例（按原文精确匹配）。
     *
     * @param definitionId 定义 ID
     * @param businessKey 业务关联键
     * @return 实例；没有时为 null
     */
    WorkflowInstance findRunningByBusinessKey(long definitionId, String businessKey);

    /** 按原版本保存状态字段并递增版本；离开 RUNNING 时清空防重槽位；实例已结束或版本不符为并发冲突。 */
    void update(WorkflowInstance row);

    /** 分页查询。 */
    List<WorkflowInstance> page(WorkflowInstanceFilter filter, long offset, int size);

    /** 与分页相同条件的总数。 */
    long count(WorkflowInstanceFilter filter);

    /**
     * 是否有其他实例的启动值引用了该文件地址。
     *
     * @param fileAddress 文件地址
     * @param excludeInstanceId 不计入的实例；可空
     * @return 有则返回 true
     */
    boolean referencedByOtherInstance(String fileAddress, Long excludeInstanceId);
}
