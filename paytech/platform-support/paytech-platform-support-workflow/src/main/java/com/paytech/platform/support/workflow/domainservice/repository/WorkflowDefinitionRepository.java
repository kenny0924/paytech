package com.paytech.platform.support.workflow.domainservice.repository;

import java.util.Collection;
import java.util.List;

import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowDefinition;
import com.paytech.platform.support.workflow.core.enums.WorkflowDefinitionStatus;

/** 流程定义（t_workflow_definition）的单表访问。 */
public interface WorkflowDefinitionRepository {

    /** 插入新定义。 */
    void insert(WorkflowDefinition row);

    /** 按主键读取；不存在为 null。 */
    WorkflowDefinition find(long definitionId);

    /** 按编码读取；不存在为 null。 */
    WorkflowDefinition findByCode(String code);

    /** 按主键批量读取。 */
    List<WorkflowDefinition> findByIds(Collection<Long> ids);

    /** 按原版本保存可变字段；版本不符为并发冲突，成功后同步版本。 */
    void update(WorkflowDefinition row);

    /** 分页查询。 */
    List<WorkflowDefinition> page(WorkflowDefinitionStatus status, String keyword, boolean launchable, long offset, int size);

    /** 与分页相同条件的总数。 */
    long count(WorkflowDefinitionStatus status, String keyword, boolean launchable);
}
