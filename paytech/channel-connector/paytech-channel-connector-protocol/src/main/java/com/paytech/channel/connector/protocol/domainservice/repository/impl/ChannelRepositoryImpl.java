package com.paytech.channel.connector.protocol.domainservice.repository.impl;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.channel.connector.api.enums.ChannelType;
import com.paytech.channel.connector.protocol.core.dal.dataobject.Channel;
import com.paytech.channel.connector.protocol.core.dal.mapper.ChannelMapper;
import com.paytech.channel.connector.protocol.core.enums.ChannelStatus;
import com.paytech.channel.connector.protocol.domain.ChannelSummary;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelRepository;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

import lombok.AllArgsConstructor;

/** 渠道仓储：校验写入行数，成功后同步原对象；不建立事务、不解释跨表资格。 */
@Repository
@AllArgsConstructor
public class ChannelRepositoryImpl implements ChannelRepository {

    private final ChannelMapper mapper;

    /** 读取指定渠道，不把缺失解释为默认配置。 */
    @Override
    public Channel byId(long channelId) {
        return mapper.byId(channelId);
    }

    /** 按编码读取渠道。 */
    @Override
    public Channel byCode(String code) {
        return mapper.byCode(code);
    }

    /** 按编码锁定渠道行。 */
    @Override
    public Channel lock(String code) {
        return mapper.lock(code);
    }

    /** 按主键锁定渠道行。 */
    @Override
    public Channel lockById(long channelId) {
        return mapper.lockById(channelId);
    }

    /** 批量读取，空集合不查询。 */
    @Override
    public List<Channel> byIds(Collection<Long> ids) {
        return ids.isEmpty() ? List.of() : mapper.byIds(ids);
    }

    /** 写入新渠道；唯一索引保护渠道编码。 */
    @Override
    public void insert(Channel row) {
        try {
            mapper.insert(row);
        } catch (DuplicateKeyException failure) {
            throw new ConcurrentRequestException("渠道编码已存在: " + row.getChannelCode(), failure);
        }
    }

    /** 修改名称与说明，成功后同步原对象。 */
    @Override
    public void updateProfile(Channel row, String channelName, String description, long updatedBy, Instant now) {
        if (mapper.updateProfile(row.getChannelId(), channelName, description, updatedBy, now) != 1) {
            throw new ConcurrentRequestException("渠道已关闭或已变化: " + row.getChannelId());
        }
        row.setChannelName(channelName);
        row.setDescription(description);
        row.setVersion(row.getVersion() + 1);
        row.setUpdatedBy(updatedBy);
        row.setUpdateTime(now);
    }

    /** 按原版本替换出站地址，成功后同步原对象。 */
    @Override
    public void updateEndpoints(Channel row, String endpoints, long updatedBy, Instant now) {
        if (row.getVersion() == Integer.MAX_VALUE
            || mapper.updateEndpoints(row.getChannelId(), row.getVersion(), endpoints, updatedBy, now) != 1) {
            throw new ConcurrentRequestException("渠道已被其他操作修改，请刷新后重试: " + row.getChannelId());
        }
        row.setEndpoints(endpoints);
        row.setVersion(row.getVersion() + 1);
        row.setUpdatedBy(updatedBy);
        row.setUpdateTime(now);
    }

    /** 以期望原状态为条件变更状态。 */
    @Override
    public void updateStatus(long channelId, ChannelStatus fromStatus, ChannelStatus toStatus, long updatedBy,
        Instant now) {
        if (mapper.updateStatus(channelId, fromStatus, toStatus, updatedBy, now) != 1) {
            throw new ConcurrentRequestException("渠道状态已变化: " + channelId);
        }
    }

    /** 分页读取列表项；无记录时不再查询列表。 */
    @Override
    public PagingResponseBody<ChannelSummary> findSummaries(ChannelType channelType, ChannelStatus status,
        String keyword, PagingRequestBody paging) {
        String prefix = escape(keyword);
        long total = mapper.countSummaries(channelType, status, prefix);
        List<ChannelSummary> items = total == 0 ? List.of()
            : mapper.selectSummaries(channelType, status, prefix, paging.offset(), paging.size());
        return new PagingResponseBody<>(items, paging.page(), paging.size(), total);
    }

    /** MySQL LIKE 前缀查询的字面量转义；空或全空白时为 null，表示不按关键字筛选。 */
    static String escape(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
