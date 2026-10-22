package com.paytech.platform.support.workflow.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowDefinitionVersion;
import com.paytech.platform.support.workflow.core.dal.mapper.WorkflowDefinitionVersionMapper;
import com.paytech.platform.support.workflow.core.enums.WorkflowVersionStatus;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowDefinitionVersionRepository;

import lombok.AllArgsConstructor;

/** 流程版本单表访问实现；条件更新成功后同步原对象。 */
@Repository
@AllArgsConstructor
public class WorkflowDefinitionVersionRepositoryImpl implements WorkflowDefinitionVersionRepository {

    private final WorkflowDefinitionVersionMapper mapper;

    /** 插入新版本。 */
    @Override
    public void insert(WorkflowDefinitionVersion row) {
        mapper.insert(row);
    }

    /** 按主键读取；不存在为 null。 */
    @Override
    public WorkflowDefinitionVersion find(long definitionVersionId) {
        return mapper.selectById(definitionVersionId);
    }

    /** 按定义与版本号读取；不存在为 null。 */
    @Override
    public WorkflowDefinitionVersion findByNo(long definitionId, int versionNo) {
        return mapper.selectByNo(definitionId, versionNo);
    }

    /** 定义的全部版本摘要（不含 JSON 内容），版本号倒序。 */
    @Override
    public List<WorkflowDefinitionVersion> summaries(long definitionId) {
        return mapper.selectSummaries(definitionId);
    }

    /** 按原版本覆盖草稿内容；版本不符或已发布为并发冲突。 */
    @Override
    public void updateDraft(WorkflowDefinitionVersion row) {
        if (mapper.updateDraft(row) != 1) {
            throw new ConcurrentRequestException("流程草稿已被其他操作修改或已发布，请刷新后重试");
        }
        row.setVersion(row.getVersion() + 1);
    }

    /** 把草稿发布为不可变版本；版本不符或已发布为并发冲突。 */
    @Override
    public void publish(WorkflowDefinitionVersion row) {
        if (mapper.publish(row) != 1) {
            throw new ConcurrentRequestException("流程草稿已被其他操作修改或已发布，请刷新后重试");
        }
        row.setVersion(row.getVersion() + 1);
        row.setStatus(WorkflowVersionStatus.PUBLISHED);
    }
}
