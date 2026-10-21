package com.paytech.channel.connector.protocol.domainservice.repository.impl;

import java.util.stream.Collectors;
import java.util.Collection;
import java.util.Map;
import com.paytech.channel.connector.protocol.core.dal.dataobject.IntegrationChildCount;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.channel.connector.protocol.core.dal.dataobject.IntegrationScript;
import com.paytech.channel.connector.protocol.core.dal.mapper.IntegrationScriptMapper;
import com.paytech.channel.connector.api.enums.ScriptExecutionStage;
import com.paytech.channel.connector.protocol.domainservice.repository.IntegrationScriptRepository;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/** 校验公共脚本写入行数，成功后同步原对象；事务由调用方持有。 */
@Repository
@AllArgsConstructor
public class IntegrationScriptRepositoryImpl implements IntegrationScriptRepository {

    private final IntegrationScriptMapper mapper;

    /** 按接入方与不可变编码读取脚本。 */
    @Override
    public IntegrationScript byCode(long integrationId, String code) {
        return mapper.byCode(integrationId, code);
    }

    /** 在调用方事务中锁定脚本行，供版本校验及条件更新。 */
    @Override
    public IntegrationScript lock(long integrationId, String code) {
        return mapper.lock(integrationId, code);
    }

    /** 读取接入方全部脚本，按编码排序；每个接入方最多 100 个。 */
    @Override
    public List<IntegrationScript> allByIntegration(long integrationId) {
        return mapper.allByIntegration(integrationId);
    }

    /** 分页查询，可按阶段筛选，按编码与标识稳定排序。 */
    @Override
    public List<IntegrationScript> list(long integrationId, String keyword, ScriptExecutionStage executionStage, long offset, int size) {
        return mapper.list(integrationId, keyword, executionStage, offset, size);
    }

    /** 使用与分页相同的筛选统计总数。 */
    @Override
    public long count(long integrationId, String keyword, ScriptExecutionStage executionStage) {
        return mapper.count(integrationId, keyword, executionStage);
    }

    /** 按接入方统计公共脚本数量；空集合返回空映射。 */
    @Override
    public Map<Long, Long> countByIntegrations(Collection<Long> integrationIds) {
        if (integrationIds.isEmpty()) {
            return Map.of();
        }
        return mapper.countByIntegrations(integrationIds).stream()
            .collect(Collectors.toMap(IntegrationChildCount::getIntegrationId, IntegrationChildCount::getChildCount));
    }

    /** 插入新脚本，接入方内编码由唯一索引保护。 */
    @Override
    public void insert(IntegrationScript row) {
        try {
            if (mapper.insert(row) != 1) {
                throw new ConcurrentRequestException("公共脚本创建未成功，请刷新后重试");
            }
        } catch (DuplicateKeyException failure) {
            if (!String.valueOf(failure.getMostSpecificCause().getMessage()).contains("uk_integration_script_code")) {
                throw failure;
            }
            throw new BizCheckFailException("脚本编码已经存在", failure);
        }
    }

    /** 按原版本更新脚本内容，并递增版本。 */
    @Override
    public void update(IntegrationScript row, String scriptName, ScriptExecutionStage executionStage, String parameterDefinition, String sourceCode, String description, long updatedBy, Instant updateTime) {
        int previousVersion = row.getVersion();
        if (previousVersion == Integer.MAX_VALUE || mapper.update(row, scriptName, executionStage, parameterDefinition, sourceCode, description, updatedBy, updateTime) != 1) {
            throw new ConcurrentRequestException("公共脚本已被其他操作修改，请刷新后重试");
        }
        row.setScriptName(scriptName);
        row.setExecutionStage(executionStage);
        row.setParameterDefinition(parameterDefinition);
        row.setSourceCode(sourceCode);
        row.setDescription(description);
        row.setVersion(previousVersion + 1);
        row.setUpdatedBy(updatedBy);
        row.setUpdateTime(updateTime);
    }

    /** 按原版本删除脚本。 */
    @Override
    public void delete(IntegrationScript row) {
        if (mapper.delete(row) != 1) {
            throw new ConcurrentRequestException("公共脚本已被其他操作修改，请刷新后重试");
        }
    }
}
