package com.paytech.channel.connector.protocol.domainservice.repository.impl;

import java.util.stream.Collectors;
import java.util.Collection;
import java.util.Map;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelChildCount;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelScript;
import com.paytech.channel.connector.protocol.core.dal.mapper.ChannelScriptMapper;
import com.paytech.channel.connector.api.enums.ScriptExecutionStage;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelScriptRepository;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/** 校验公共脚本写入行数，成功后同步原对象；事务由调用方持有。 */
@Repository
@AllArgsConstructor
public class ChannelScriptRepositoryImpl implements ChannelScriptRepository {

    private final ChannelScriptMapper mapper;

    /** 按渠道与不可变编码读取脚本。 */
    @Override
    public ChannelScript byCode(long channelId, String code) {
        return mapper.byCode(channelId, code);
    }

    /** 在调用方事务中锁定脚本行，供版本校验及条件更新。 */
    @Override
    public ChannelScript lock(long channelId, String code) {
        return mapper.lock(channelId, code);
    }

    /** 读取渠道全部脚本，按编码排序；每个渠道最多 100 个。 */
    @Override
    public List<ChannelScript> allByChannel(long channelId) {
        return mapper.allByChannel(channelId);
    }

    /** 分页查询，可按阶段筛选，按编码与标识稳定排序。 */
    @Override
    public List<ChannelScript> list(long channelId, String keyword, ScriptExecutionStage executionStage, long offset, int size) {
        return mapper.list(channelId, keyword, executionStage, offset, size);
    }

    /** 使用与分页相同的筛选统计总数。 */
    @Override
    public long count(long channelId, String keyword, ScriptExecutionStage executionStage) {
        return mapper.count(channelId, keyword, executionStage);
    }

    /** 按渠道统计公共脚本数量；空集合返回空映射。 */
    @Override
    public Map<Long, Long> countByChannels(Collection<Long> channelIds) {
        if (channelIds.isEmpty()) {
            return Map.of();
        }
        return mapper.countByChannels(channelIds).stream()
            .collect(Collectors.toMap(ChannelChildCount::getChannelId, ChannelChildCount::getChildCount));
    }

    /** 插入新脚本，渠道内编码由唯一索引保护。 */
    @Override
    public void insert(ChannelScript row) {
        try {
            if (mapper.insert(row) != 1) {
                throw new ConcurrentRequestException("公共脚本创建未成功，请刷新后重试");
            }
        } catch (DuplicateKeyException failure) {
            if (!String.valueOf(failure.getMostSpecificCause().getMessage()).contains("uk_channel_script_code")) {
                throw failure;
            }
            throw new BizCheckFailException("脚本编码已经存在", failure);
        }
    }

    /** 按原版本更新脚本内容，并递增版本。 */
    @Override
    public void update(ChannelScript row, String scriptName, ScriptExecutionStage executionStage, String parameterDefinition, String sourceCode, String description, long updatedBy, Instant updateTime) {
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
    public void delete(ChannelScript row) {
        if (mapper.delete(row) != 1) {
            throw new ConcurrentRequestException("公共脚本已被其他操作修改，请刷新后重试");
        }
    }
}
