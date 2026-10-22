package com.paytech.platform.support.workflow.domainservice.repository.impl;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowDefinition;
import com.paytech.platform.support.workflow.core.dal.mapper.WorkflowDefinitionMapper;
import com.paytech.platform.support.workflow.core.enums.WorkflowDefinitionStatus;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowDefinitionRepository;

import lombok.AllArgsConstructor;

/** 流程定义单表访问实现；条件更新成功后同步原对象。 */
@Repository
@AllArgsConstructor
public class WorkflowDefinitionRepositoryImpl implements WorkflowDefinitionRepository {

    private final WorkflowDefinitionMapper mapper;

    /** 插入新定义。 */
    @Override
    public void insert(WorkflowDefinition row) {
        mapper.insert(row);
    }

    /** 按主键读取；不存在为 null。 */
    @Override
    public WorkflowDefinition find(long definitionId) {
        return mapper.selectById(definitionId);
    }

    /** 按编码读取；不存在为 null。 */
    @Override
    public WorkflowDefinition findByCode(String code) {
        return mapper.selectByCode(code);
    }

    /** 按主键批量读取。 */
    @Override
    public List<WorkflowDefinition> findByIds(Collection<Long> ids) {
        return ids.isEmpty() ? List.of() : mapper.selectByIds(ids);
    }

    /** 按原版本保存可变字段；版本不符为并发冲突，成功后同步版本。 */
    @Override
    public void update(WorkflowDefinition row) {
        if (mapper.update(row) != 1) {
            throw new ConcurrentRequestException("流程定义已被其他操作修改，请刷新后重试");
        }
        row.setVersion(row.getVersion() + 1);
    }

    /** 分页查询。 */
    @Override
    public List<WorkflowDefinition> page(WorkflowDefinitionStatus status, String keyword, boolean launchable, long offset, int size) {
        return mapper.page(status, keyword, launchable, offset, size);
    }

    /** 与分页相同条件的总数。 */
    @Override
    public long count(WorkflowDefinitionStatus status, String keyword, boolean launchable) {
        return mapper.count(status, keyword, launchable);
    }
}
