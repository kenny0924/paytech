package com.paytech.channel.connector.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.channel.connector.core.dal.dataobject.ChannelMid;
import com.paytech.channel.connector.core.dal.mapper.ChannelMidMapper;
import com.paytech.channel.connector.core.enums.ChannelStatus;
import com.paytech.channel.connector.domain.ChannelMidListCriteria;
import com.paytech.channel.connector.domain.ChannelMidSummaryView;
import com.paytech.channel.connector.domainservice.repository.ChannelMidRepository;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.foundation.common.error.ConcurrentRequestException;

import lombok.AllArgsConstructor;

/** ChannelMid 仓储不建立事务、不解释跨表资格。 */
@Repository
@AllArgsConstructor
public class ChannelMidRepositoryImpl implements ChannelMidRepository {
    /** 本表 SQL 映射。 */
    private final ChannelMidMapper mapper;

    /** 读取本表范围内的配置。 */
    @Override
    public List<ChannelMid> findByAccountIds(List<Long> ids) {
        return ids.isEmpty() ? List.of() : mapper.findByAccountIds(ids);
    }

    /** 读取指定记录，不把缺失解释为默认配置。 */
    @Override
    public ChannelMid findById(long id) {
        return mapper.findById(id);
    }

    /** 相同筛选条件下数据库 COUNT 与 LIMIT/OFFSET 分页；无记录时不再查询列表。 */
    @Override
    public PagingResponseBody<ChannelMidSummaryView> findPage(ChannelMidListCriteria criteria) {
        var paging = criteria.getPaging();
        long total = mapper.countSummaries(criteria);
        List<ChannelMidSummaryView> items = total == 0 ? List.of()
            : mapper.selectSummaries(criteria, paging.offset(), paging.size());
        return new PagingResponseBody<>(items, paging.page(), paging.size(), total);
    }

    /** 写入新 MID；唯一索引保护同渠道外部 MID。 */
    @Override
    public void insert(ChannelMid mid) {
        try {
            mapper.insert(mid);
        } catch (DuplicateKeyException e) {
            throw new ConcurrentRequestException("同渠道外部 MID 已存在: 渠道 " + mid.getChannelId() + ", MID "
                + mid.getExternalMidCode(), e);
        }
    }

    /** 以原绑定账号为条件改绑，成功后同步原对象。 */
    @Override
    public void updateAccount(ChannelMid mid, long toAccountId, Instant now) {
        if (mapper.updateAccount(mid.getMidId(), mid.getChannelAccountId(), toAccountId, now) != 1) {
            throw new ConcurrentRequestException("MID 已关闭或绑定已变化: " + mid.getMidId());
        }
        mid.setChannelAccountId(toAccountId);
        mid.setUpdateTime(now);
    }

    /** 以原状态为条件变更状态，成功后同步原对象。 */
    @Override
    public void updateStatus(ChannelMid mid, ChannelStatus toStatus, Instant activatedAt, Instant closedAt,
        Instant now) {
        if (mapper.updateStatus(mid.getMidId(), mid.getMidStatus(), toStatus, activatedAt, closedAt, now) != 1) {
            throw new ConcurrentRequestException("MID 状态已变化: " + mid.getMidId());
        }
        mid.setMidStatus(toStatus);
        mid.setActivatedAt(activatedAt);
        mid.setClosedAt(closedAt);
        mid.setUpdateTime(now);
    }

    /** 统计渠道下未关闭的 MID 数。 */
    @Override
    public long countOpenByChannel(long channelId) {
        return mapper.countOpenByChannel(channelId);
    }

    /** 统计当前绑定该账号且未关闭的 MID 数。 */
    @Override
    public long countOpenByAccount(long channelAccountId) {
        return mapper.countOpenByAccount(channelAccountId);
    }
}
