package com.paytech.platform.support.workflow.domainservice.repository;

import java.util.List;

import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowDefinitionVersion;

/** 流程版本（t_workflow_definition_version）的单表访问。 */
public interface WorkflowDefinitionVersionRepository {

    /** 插入新版本。 */
    void insert(WorkflowDefinitionVersion row);

    /** 按主键读取；不存在为 null。 */
    WorkflowDefinitionVersion find(long definitionVersionId);

    /** 按定义与版本号读取；不存在为 null。 */
    WorkflowDefinitionVersion findByNo(long definitionId, int versionNo);

    /** 定义的全部版本摘要（不含 JSON 内容），版本号倒序。 */
    List<WorkflowDefinitionVersion> summaries(long definitionId);

    /** 按原版本覆盖草稿内容；版本不符或已发布为并发冲突。 */
    void updateDraft(WorkflowDefinitionVersion row);

    /** 把草稿发布为不可变版本；版本不符或已发布为并发冲突。 */
    void publish(WorkflowDefinitionVersion row);
}
