package com.paytech.platform.support.workflow.domainservice.service;

import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowActionTemplate;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowDefinition;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowDefinitionVersion;

/** 流程定义、版本与动作模板的本地保存；必须在调用方已开启的本地事务中调用，版本不符即并发冲突。 */
public interface WorkflowDefinitionService {

    /**
     * 新建定义及其草稿 v1。
     *
     * @param definition 新定义
     * @param draft 新草稿
     */
    void create(WorkflowDefinition definition, WorkflowDefinitionVersion draft);

    /**
     * 保存草稿：已有草稿按原版本覆盖，否则插入新草稿；定义的名称、说明与草稿指针按原版本更新。
     *
     * @param definition 已改好字段的定义
     * @param draft 已改好内容的草稿
     * @param newDraft 草稿是否为本次新建
     */
    void saveDraft(WorkflowDefinition definition, WorkflowDefinitionVersion draft, boolean newDraft);

    /**
     * 发布草稿并切换定义的当前版本与草稿指针。
     *
     * @param definition 已改好指针的定义
     * @param draft 已写入快照的草稿
     */
    void publish(WorkflowDefinition definition, WorkflowDefinitionVersion draft);

    /**
     * 保存定义的启停状态。
     *
     * @param definition 已改好状态的定义
     */
    void changeStatus(WorkflowDefinition definition);

    /**
     * 新建动作模板。
     *
     * @param template 新模板
     */
    void createTemplate(WorkflowActionTemplate template);

    /**
     * 按原版本保存动作模板的修改或启停。
     *
     * @param template 已改好字段的模板
     */
    void updateTemplate(WorkflowActionTemplate template);
}
