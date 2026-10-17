package com.paytech.platform.support.integration.domainservice.repository.impl;

import java.util.stream.Collectors;
import java.util.Collection;
import java.util.Map;
import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationChildCount;
import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationApiEndpoint;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationApi;
import com.paytech.platform.support.integration.core.dal.mapper.IntegrationApiMapper;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationApiRepository;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/** 校验渠道原子 API写入行数，成功后同步原对象；事务由调用方持有。 */
@Repository
@AllArgsConstructor
public class IntegrationApiRepositoryImpl implements IntegrationApiRepository {

    private final IntegrationApiMapper mapper;

    /** 按接入方与不可变编码读取原子 API。 */
    @Override
    public IntegrationApi byCode(long integrationId, String code) {
        return mapper.byCode(integrationId, code);
    }

    /** 在调用方事务中锁定原子 API 行，供版本校验及条件更新。 */
    @Override
    public IntegrationApi lock(long integrationId, String code) {
        return mapper.lock(integrationId, code);
    }

    /** 读取接入方全部原子 API，按编码排序；每个接入方最多 200 个。 */
    @Override
    public List<IntegrationApi> allByIntegration(long integrationId) {
        return mapper.allByIntegration(integrationId);
    }

    /** 分页查询，按编码与标识稳定排序。 */
    @Override
    public List<IntegrationApi> list(long integrationId, String keyword, long offset, int size) {
        return mapper.list(integrationId, keyword, offset, size);
    }

    /** 使用与分页相同的筛选统计总数。 */
    @Override
    public long count(long integrationId, String keyword) {
        return mapper.count(integrationId, keyword);
    }

    /** 按接入方统计原子 API 数量；空集合返回空映射。 */
    @Override
    public Map<Long, Long> countByIntegrations(Collection<Long> integrationIds) {
        if (integrationIds.isEmpty()) {
            return Map.of();
        }
        return mapper.countByIntegrations(integrationIds).stream()
            .collect(Collectors.toMap(IntegrationChildCount::getIntegrationId, IntegrationChildCount::getChildCount));
    }

    /** 读取接入方全部原子 API 引用的出站地址代码，按编码排序。 */
    @Override
    public List<IntegrationApiEndpoint> endpointReferences(long integrationId) {
        return mapper.endpointReferences(integrationId);
    }

    /** 插入新原子 API，接入方内编码由唯一索引保护。 */
    @Override
    public void insert(IntegrationApi row) {
        try {
            if (mapper.insert(row) != 1) {
                throw new ConcurrentRequestException("渠道原子 API创建未成功，请刷新后重试");
            }
        } catch (DuplicateKeyException failure) {
            if (!String.valueOf(failure.getMostSpecificCause().getMessage()).contains("uk_integration_api_code")) {
                throw failure;
            }
            throw new BizCheckFailException("原子 API 编码已经存在", failure);
        }
    }

    /** 按原版本更新名称、说明与执行定义，并递增版本。 */
    @Override
    public void update(IntegrationApi row, String apiName, String description, String definitionJson, long updatedBy, Instant updateTime) {
        int previousVersion = row.getVersion();
        if (previousVersion == Integer.MAX_VALUE || mapper.update(row, apiName, description, definitionJson, updatedBy, updateTime) != 1) {
            throw new ConcurrentRequestException("渠道原子 API已被其他操作修改，请刷新后重试");
        }
        row.setApiName(apiName);
        row.setDescription(description);
        row.setDefinitionJson(definitionJson);
        row.setVersion(previousVersion + 1);
        row.setUpdatedBy(updatedBy);
        row.setUpdateTime(updateTime);
    }

    /** 按原版本删除原子 API。 */
    @Override
    public void delete(IntegrationApi row) {
        if (mapper.delete(row) != 1) {
            throw new ConcurrentRequestException("渠道原子 API已被其他操作修改，请刷新后重试");
        }
    }
}
