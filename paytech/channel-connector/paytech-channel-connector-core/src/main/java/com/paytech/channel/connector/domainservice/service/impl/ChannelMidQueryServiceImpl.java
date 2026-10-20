package com.paytech.channel.connector.domainservice.service.impl;

import org.springframework.stereotype.Service;

import com.paytech.channel.connector.domain.ChannelMidListCriteria;
import com.paytech.channel.connector.domain.ChannelMidSummaryView;
import com.paytech.channel.connector.domainservice.repository.ChannelMidRepository;
import com.paytech.channel.connector.domainservice.service.ChannelMidQueryService;
import com.paytech.foundation.web.paging.PagingResponseBody;

import lombok.AllArgsConstructor;

/** MID 列表查询；普通列表读取不建事务。 */
@Service
@AllArgsConstructor
public class ChannelMidQueryServiceImpl implements ChannelMidQueryService {
    private final ChannelMidRepository channelMidRepository;

    /** 委托仓储按条件分页读取。 */
    @Override
    public PagingResponseBody<ChannelMidSummaryView> listMids(ChannelMidListCriteria criteria) {
        return channelMidRepository.findPage(criteria);
    }
}
