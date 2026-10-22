package com.paytech.platform.support.workflow.domainservice.repository.impl;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowActionTemplate;
import com.paytech.platform.support.workflow.core.dal.mapper.WorkflowActionTemplateMapper;
import com.paytech.platform.support.workflow.core.enums.ActionTemplateStatus;
import com.paytech.platform.support.workflow.core.enums.WorkflowActionType;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowActionTemplateRepository;

import lombok.AllArgsConstructor;

/** 动作模板单表访问实现；条件更新成功后同步原对象。 */
@Repository
@AllArgsConstructor
public class WorkflowActionTemplateRepositoryImpl implements WorkflowActionTemplateRepository {

    private final WorkflowActionTemplateMapper mapper;

    /** 插入新模板。 */
    @Override
    public void insert(WorkflowActionTemplate row) {
        mapper.insert(row);
    }

    /** 按主键读取；不存在为 null。 */
    @Override
    public WorkflowActionTemplate find(long templateId) {
        return mapper.selectById(templateId);
    }

    /** 按编码读取；不存在为 null。 */
    @Override
    public WorkflowActionTemplate findByCode(String code) {
        return mapper.selectByCode(code);
    }

    /** 按主键批量读取。 */
    @Override
    public List<WorkflowActionTemplate> findByIds(Collection<Long> ids) {
        return ids.isEmpty() ? List.of() : mapper.selectByIds(ids);
    }

    /** 按原版本保存可变字段；版本不符为并发冲突，成功后同步版本。 */
    @Override
    public void update(WorkflowActionTemplate row) {
        if (mapper.update(row) != 1) {
            throw new ConcurrentRequestException("动作模板已被其他操作修改，请刷新后重试");
        }
        row.setVersion(row.getVersion() + 1);
    }

    /** 分页查询。 */
    @Override
    public List<WorkflowActionTemplate> page(WorkflowActionType actionType, ActionTemplateStatus status, String keyword, long offset, int size) {
        return mapper.page(actionType, status, keyword, offset, size);
    }

    /** 与分页相同条件的总数。 */
    @Override
    public long count(WorkflowActionType actionType, ActionTemplateStatus status, String keyword) {
        return mapper.count(actionType, status, keyword);
    }
}
