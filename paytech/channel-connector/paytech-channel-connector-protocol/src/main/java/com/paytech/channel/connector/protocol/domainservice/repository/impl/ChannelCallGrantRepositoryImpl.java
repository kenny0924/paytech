package com.paytech.channel.connector.protocol.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.channel.connector.api.enums.ChannelCallGrantStatus;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelCallGrant;
import com.paytech.channel.connector.protocol.core.dal.mapper.ChannelCallGrantMapper;
import com.paytech.channel.connector.protocol.domain.ChannelCallGrantView;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelCallGrantRepository;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

import lombok.AllArgsConstructor;

/** 调用授权仓储：校验写入行数，成功后同步原对象；不建立事务。 */
@Repository
@AllArgsConstructor
public class ChannelCallGrantRepositoryImpl implements ChannelCallGrantRepository {

    private final ChannelCallGrantMapper mapper;

    /** 按主键读取。 */
    @Override
    public ChannelCallGrant byId(long callGrantId) {
        return mapper.byId(callGrantId);
    }

    /** 按唯一组合读取。 */
    @Override
    public ChannelCallGrant byKey(String callerService, long channelId, long standardApiId) {
        return mapper.byKey(callerService, channelId, standardApiId);
    }

    /** 插入授权；并发插入同一组合时唯一索引冲突，按 409 拒绝。 */
    @Override
    public void insert(ChannelCallGrant row) {
        try {
            mapper.insert(row);
        } catch (DuplicateKeyException failure) {
            throw new ConcurrentRequestException("调用授权已存在: 调用方 " + row.getCallerService() + ", 渠道 "
                + row.getChannelId() + ", 标准 API " + row.getStandardApiId(), failure);
        }
    }

    /** 以原版本与原状态为条件变更状态，成功后同步原对象。 */
    @Override
    public void updateStatus(ChannelCallGrant row, ChannelCallGrantStatus toStatus, long updatedBy, Instant now) {
        if (row.getVersion() == Integer.MAX_VALUE || mapper.updateStatus(row.getCallGrantId(), row.getVersion(),
            row.getGrantStatus(), toStatus, updatedBy, now) != 1) {
            throw new ConcurrentRequestException("调用授权已被其他操作修改，请刷新后重试: " + row.getCallGrantId());
        }
        row.setGrantStatus(toStatus);
        row.setVersion(row.getVersion() + 1);
        row.setUpdatedBy(updatedBy);
        row.setUpdateTime(now);
    }

    /** 是否存在 ACTIVE 授权。 */
    @Override
    public boolean hasActive(String callerService, long channelId, String standardApiCode) {
        return mapper.countActive(callerService, channelId, standardApiCode) > 0;
    }

    /** 渠道该标准 API 的 ACTIVE 授权数。 */
    @Override
    public long countActiveByStandardApi(long channelId, long standardApiId) {
        return mapper.countActiveByStandardApi(channelId, standardApiId);
    }

    /** 分页读取；无记录时不再查询列表。 */
    @Override
    public PagingResponseBody<ChannelCallGrantView> findViews(Long channelId, String callerService,
        String standardApiCode, ChannelCallGrantStatus grantStatus, PagingRequestBody paging) {
        long total = mapper.countViews(channelId, callerService, standardApiCode, grantStatus);
        List<ChannelCallGrantView> items = total == 0 ? List.of() : mapper.selectViews(channelId, callerService,
            standardApiCode, grantStatus, paging.offset(), paging.size());
        return new PagingResponseBody<>(items, paging.page(), paging.size(), total);
    }
}
