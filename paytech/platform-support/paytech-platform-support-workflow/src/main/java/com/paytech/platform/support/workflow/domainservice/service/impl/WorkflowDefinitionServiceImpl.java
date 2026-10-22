package com.paytech.platform.support.workflow.domainservice.service.impl;

import org.springframework.stereotype.Service;

import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowActionTemplate;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowDefinition;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowDefinitionVersion;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowActionTemplateRepository;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowDefinitionRepository;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowDefinitionVersionRepository;
import com.paytech.platform.support.workflow.domainservice.service.WorkflowDefinitionService;

import lombok.AllArgsConstructor;

/** 配置表的本地保存；编码唯一由唯一索引最终保护，版本由条件更新保护。 */
@Service
@AllArgsConstructor
public class WorkflowDefinitionServiceImpl implements WorkflowDefinitionService {

    private final WorkflowDefinitionRepository definitions;
    private final WorkflowActionTemplateRepository templates;
    private final WorkflowDefinitionVersionRepository versions;

    /** 先插定义再插草稿。 */
    @Override
    public void create(WorkflowDefinition definition, WorkflowDefinitionVersion draft) {
        definitions.insert(definition);
        versions.insert(draft);
    }

    /** 草稿内容与定义指针一起保存。 */
    @Override
    public void saveDraft(WorkflowDefinition definition, WorkflowDefinitionVersion draft, boolean newDraft) {
        definitions.update(definition);
        if (newDraft) {
            versions.insert(draft);
        } else {
            versions.updateDraft(draft);
        }
    }

    /** 先冻结版本，再切换定义指针。 */
    @Override
    public void publish(WorkflowDefinition definition, WorkflowDefinitionVersion draft) {
        versions.publish(draft);
        definitions.update(definition);
    }

    /** 按原版本保存启停。 */
    @Override
    public void changeStatus(WorkflowDefinition definition) {
        definitions.update(definition);
    }

    /** 插入新模板。 */
    @Override
    public void createTemplate(WorkflowActionTemplate template) {
        templates.insert(template);
    }

    /** 按原版本保存模板。 */
    @Override
    public void updateTemplate(WorkflowActionTemplate template) {
        templates.update(template);
    }
}
