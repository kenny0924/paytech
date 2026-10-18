package com.paytech.platform.support.integration.domainservice.repository.impl;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationImplementation;
import com.paytech.platform.support.integration.core.dal.mapper.IntegrationImplementationMapper;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationImplementationRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/** 校验渠道标准 API 实现写入行数，成功后同步原对象；事务由调用方持有。 */
@Repository
@AllArgsConstructor
public class IntegrationImplementationRepositoryImpl implements IntegrationImplementationRepository {

    private final IntegrationImplementationMapper mapper;

    /** 按接入方与标准 API 读取当前实现。 */
    @Override
    public IntegrationImplementation byKey(long integrationId, long standardApiId) {
        return mapper.byKey(integrationId, standardApiId);
    }

    /** 在调用方事务中锁定实现行，供版本校验及条件更新。 */
    @Override
    public IntegrationImplementation lock(long integrationId, long standardApiId) {
        return mapper.lock(integrationId, standardApiId);
    }

    /** 读取接入方的全部实现。 */
    @Override
    public List<IntegrationImplementation> allByIntegration(long integrationId) {
        return mapper.allByIntegration(integrationId);
    }

    /** 读取实现某个标准 API 的全部接入方实现，供引用保护与影响校验。 */
    @Override
    public List<IntegrationImplementation> allByStandardApi(long standardApiId) {
        return mapper.allByStandardApi(standardApiId);
    }

    /** 按接入方批量读取实现，供列表展示已实现的标准 API。 */
    @Override
    public List<IntegrationImplementation> allByIntegrations(Collection<Long> integrationIds) {
        if (integrationIds.isEmpty()) {
            return List.of();
        }
        return mapper.allByIntegrations(integrationIds);
    }

    /** 按标准 API 批量读取实现；空集合返回空列表。 */
    @Override
    public List<IntegrationImplementation> allByStandardApis(Collection<Long> standardApiIds) {
        return standardApiIds.isEmpty() ? List.of() : mapper.allByStandardApis(standardApiIds);
    }

    /** 插入新实现，同一接入方与标准 API 由唯一索引保护。 */
    @Override
    public void insert(IntegrationImplementation row) {
        try {
            if (mapper.insert(row) != 1) {
                throw new ConcurrentRequestException("渠道标准 API 实现创建未成功，请刷新后重试");
            }
        } catch (DuplicateKeyException failure) {
            if (!String.valueOf(failure.getMostSpecificCause().getMessage()).contains("uk_integration_standard_api")) {
                throw failure;
            }
            throw new BizCheckFailException("该接入方已实现此标准 API", failure);
        }
    }

    /** 按原版本更新流程定义，并递增版本。 */
    @Override
    public void update(IntegrationImplementation row, String flowDefinitionJson, long updatedBy, Instant updateTime) {
        int previousVersion = row.getVersion();
        if (previousVersion == Integer.MAX_VALUE || mapper.update(row, flowDefinitionJson, updatedBy, updateTime) != 1) {
            throw new ConcurrentRequestException("渠道标准 API 实现已被其他操作修改，请刷新后重试");
        }
        row.setFlowDefinitionJson(flowDefinitionJson);
        row.setVersion(previousVersion + 1);
        row.setUpdatedBy(updatedBy);
        row.setUpdateTime(updateTime);
    }

    /** 按原版本删除实现。 */
    @Override
    public void delete(IntegrationImplementation row) {
        if (mapper.delete(row) != 1) {
            throw new ConcurrentRequestException("渠道标准 API 实现已被其他操作修改，请刷新后重试");
        }
    }
}
