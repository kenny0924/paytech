package com.paytech.channel.connector.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.channel.connector.core.dal.dataobject.PaymentChannel;
import com.paytech.channel.connector.core.dal.mapper.PaymentChannelMapper;
import com.paytech.channel.connector.core.enums.ChannelStatus;
import com.paytech.channel.connector.domain.ChannelSummaryView;
import com.paytech.channel.connector.domainservice.repository.PaymentChannelRepository;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

import lombok.AllArgsConstructor;

/** PaymentChannel 仓储不建立事务、不解释跨表资格。 */
@Repository
@AllArgsConstructor
public class PaymentChannelRepositoryImpl implements PaymentChannelRepository {
    /** 本表 SQL 映射。 */
    private final PaymentChannelMapper mapper;

    /** 读取本表范围内的配置。 */
    @Override
    public List<PaymentChannel> findByIds(List<Long> ids) {
        return ids.isEmpty() ? List.of() : mapper.findByIds(ids);
    }

    /** 读取指定记录，不把缺失解释为默认配置。 */
    @Override
    public PaymentChannel findById(long id) {
        return mapper.findById(id);
    }

    /** 写入新渠道；唯一索引保护渠道编码。 */
    @Override
    public void insert(PaymentChannel channel) {
        try {
            mapper.insert(channel);
        } catch (DuplicateKeyException e) {
            throw new ConcurrentRequestException("渠道编码已存在: " + channel.getChannelCode(), e);
        }
    }

    /** 未关闭渠道改名，成功后同步原对象。 */
    @Override
    public void updateName(PaymentChannel channel, String channelName, Instant now) {
        if (mapper.updateName(channel.getChannelId(), channelName, now) != 1) {
            throw new ConcurrentRequestException("渠道已关闭或已变化: " + channel.getChannelId());
        }
        channel.setChannelName(channelName);
        channel.setUpdateTime(now);
    }

    /** 以原状态为条件变更状态，成功后同步原对象。 */
    @Override
    public void updateStatus(PaymentChannel channel, ChannelStatus toStatus, Instant now) {
        if (mapper.updateStatus(channel.getChannelId(), channel.getChannelStatus(), toStatus, now) != 1) {
            throw new ConcurrentRequestException("渠道状态已变化: " + channel.getChannelId());
        }
        channel.setChannelStatus(toStatus);
        channel.setUpdateTime(now);
    }

    /** 分页读取列表项；无记录时不再查询列表。 */
    @Override
    public PagingResponseBody<ChannelSummaryView> findSummaries(ChannelStatus status, String keyword,
        PagingRequestBody paging) {
        String prefix = LikePatterns.escape(keyword);
        long total = mapper.countSummaries(status, prefix);
        List<ChannelSummaryView> items =
            total == 0 ? List.of() : mapper.selectSummaries(status, prefix, paging.offset(), paging.size());
        return new PagingResponseBody<>(items, paging.page(), paging.size(), total);
    }
}
