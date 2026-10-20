package com.paytech.channel.connector.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.channel.connector.core.dal.dataobject.ChannelConfigChange;
import com.paytech.channel.connector.core.dal.mapper.ChannelConfigChangeMapper;
import com.paytech.channel.connector.core.enums.ChangeTargetType;
import com.paytech.channel.connector.domainservice.repository.ChannelConfigChangeRepository;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

import lombok.AllArgsConstructor;

/** ChannelConfigChangeRepository 的 MyBatis 实现；不建立事务。 */
@Repository
@AllArgsConstructor
public class ChannelConfigChangeRepositoryImpl implements ChannelConfigChangeRepository {
    private final ChannelConfigChangeMapper mapper;

    /** 写入变更记录；行数不为 1 属于系统错误。 */
    @Override
    public void insert(ChannelConfigChange change) {
        if (mapper.insert(change) != 1) {
            throw new ErrorException("渠道配置变更记录写入失败: " + change.getTargetType().getCode() + ", "
                + change.getTargetId());
        }
    }

    /** 按被变更对象分页读取，按主键降序；无记录时不再查询列表。 */
    @Override
    public PagingResponseBody<ChannelConfigChange> findPage(ChangeTargetType targetType, long targetId,
        PagingRequestBody paging) {
        long total = mapper.countByTarget(targetType, targetId);
        List<ChannelConfigChange> items = total == 0 ? List.of()
            : mapper.selectByTarget(targetType, targetId, paging.offset(), paging.size());
        return new PagingResponseBody<>(items, paging.page(), paging.size(), total);
    }
}
