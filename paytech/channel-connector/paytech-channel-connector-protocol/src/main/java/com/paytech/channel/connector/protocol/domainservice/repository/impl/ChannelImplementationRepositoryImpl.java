package com.paytech.channel.connector.protocol.domainservice.repository.impl;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelImplementation;
import com.paytech.channel.connector.protocol.core.dal.mapper.ChannelImplementationMapper;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelImplementationRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/** 校验渠道标准 API 实现写入行数，成功后同步原对象；事务由调用方持有。 */
@Repository
@AllArgsConstructor
public class ChannelImplementationRepositoryImpl implements ChannelImplementationRepository {

    private final ChannelImplementationMapper mapper;

    /** 按渠道与标准 API 读取当前实现。 */
    @Override
    public ChannelImplementation byKey(long channelId, long standardApiId) {
        return mapper.byKey(channelId, standardApiId);
    }

    /** 在调用方事务中锁定实现行，供版本校验及条件更新。 */
    @Override
    public ChannelImplementation lock(long channelId, long standardApiId) {
        return mapper.lock(channelId, standardApiId);
    }

    /** 读取渠道的全部实现。 */
    @Override
    public List<ChannelImplementation> allByChannel(long channelId) {
        return mapper.allByChannel(channelId);
    }

    /** 读取实现某个标准 API 的全部渠道实现，供引用保护与影响校验。 */
    @Override
    public List<ChannelImplementation> allByStandardApi(long standardApiId) {
        return mapper.allByStandardApi(standardApiId);
    }

    /** 按渠道批量读取实现，供列表展示已实现的标准 API。 */
    @Override
    public List<ChannelImplementation> allByChannels(Collection<Long> channelIds) {
        if (channelIds.isEmpty()) {
            return List.of();
        }
        return mapper.allByChannels(channelIds);
    }

    /** 按标准 API 批量读取实现；空集合返回空列表。 */
    @Override
    public List<ChannelImplementation> allByStandardApis(Collection<Long> standardApiIds) {
        return standardApiIds.isEmpty() ? List.of() : mapper.allByStandardApis(standardApiIds);
    }

    /** 插入新实现，同一渠道与标准 API 由唯一索引保护。 */
    @Override
    public void insert(ChannelImplementation row) {
        try {
            if (mapper.insert(row) != 1) {
                throw new ConcurrentRequestException("渠道标准 API 实现创建未成功，请刷新后重试");
            }
        } catch (DuplicateKeyException failure) {
            if (!String.valueOf(failure.getMostSpecificCause().getMessage()).contains("uk_channel_standard_api")) {
                throw failure;
            }
            throw new BizCheckFailException("该渠道已实现此标准 API", failure);
        }
    }

    /** 按原版本更新流程定义，并递增版本。 */
    @Override
    public void update(ChannelImplementation row, String flowDefinitionJson, long updatedBy, Instant updateTime) {
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
    public void delete(ChannelImplementation row) {
        if (mapper.delete(row) != 1) {
            throw new ConcurrentRequestException("渠道标准 API 实现已被其他操作修改，请刷新后重试");
        }
    }
}
