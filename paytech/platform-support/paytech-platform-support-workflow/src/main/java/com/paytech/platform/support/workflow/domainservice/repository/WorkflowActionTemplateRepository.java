package com.paytech.platform.support.workflow.domainservice.repository;

import java.util.Collection;
import java.util.List;

import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowActionTemplate;
import com.paytech.platform.support.workflow.core.enums.ActionTemplateStatus;
import com.paytech.platform.support.workflow.core.enums.WorkflowActionType;

/** 动作模板（t_workflow_action_template）的单表访问。 */
public interface WorkflowActionTemplateRepository {

    /** 插入新模板。 */
    void insert(WorkflowActionTemplate row);

    /** 按主键读取；不存在为 null。 */
    WorkflowActionTemplate find(long templateId);

    /** 按编码读取；不存在为 null。 */
    WorkflowActionTemplate findByCode(String code);

    /** 按主键批量读取。 */
    List<WorkflowActionTemplate> findByIds(Collection<Long> ids);

    /** 按原版本保存可变字段；版本不符为并发冲突，成功后同步版本。 */
    void update(WorkflowActionTemplate row);

    /** 分页查询。 */
    List<WorkflowActionTemplate> page(WorkflowActionType actionType, ActionTemplateStatus status, String keyword, long offset, int size);

    /** 与分页相同条件的总数。 */
    long count(WorkflowActionType actionType, ActionTemplateStatus status, String keyword);
}
