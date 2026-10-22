package com.paytech.platform.support.workflow.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.platform.support.workflow.core.common.WorkflowValueFormats;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowInstance;
import com.paytech.platform.support.workflow.core.dal.mapper.WorkflowInstanceMapper;
import com.paytech.platform.support.workflow.domain.query.WorkflowInstanceFilter;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowInstanceRepository;

import lombok.AllArgsConstructor;

/** 流程实例单表访问实现；条件更新成功后同步原对象。 */
@Repository
@AllArgsConstructor
public class WorkflowInstanceRepositoryImpl implements WorkflowInstanceRepository {

    private final WorkflowInstanceMapper mapper;

    /** 插入新实例。 */
    @Override
    public void insert(WorkflowInstance row) {
        mapper.insert(row);
    }

    /** 按主键读取；不存在为 null。 */
    @Override
    public WorkflowInstance find(long instanceId) {
        return mapper.selectById(instanceId);
    }

    /** 按原版本保存状态字段并递增版本；实例已结束或版本不符为并发冲突。 */
    @Override
    public void update(WorkflowInstance row) {
        if (mapper.update(row) != 1) {
            throw new ConcurrentRequestException("流程实例已被其他操作修改，请刷新后重试");
        }
        row.setVersion(row.getVersion() + 1);
    }

    /** 分页查询。 */
    @Override
    public List<WorkflowInstance> page(WorkflowInstanceFilter filter, long offset, int size) {
        return mapper.page(filter, offset, size);
    }

    /** 与分页相同条件的总数。 */
    @Override
    public long count(WorkflowInstanceFilter filter) {
        return mapper.count(filter);
    }

    /** 按第二层数组元素精确匹配文件地址（转义 LIKE 通配符）。 */
    @Override
    public boolean referencedByOtherInstance(String fileAddress, Long excludeInstanceId) {
        return !mapper.selectIdsReferencingFile(WorkflowValueFormats.likeLiteral(fileAddress), excludeInstanceId).isEmpty();
    }
}
