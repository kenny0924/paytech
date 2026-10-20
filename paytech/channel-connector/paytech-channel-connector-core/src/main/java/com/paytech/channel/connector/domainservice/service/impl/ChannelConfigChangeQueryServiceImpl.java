package com.paytech.channel.connector.domainservice.service.impl;

import org.springframework.stereotype.Service;

import com.paytech.channel.connector.core.dal.dataobject.ChannelConfigChange;
import com.paytech.channel.connector.core.enums.ChangeTargetType;
import com.paytech.channel.connector.domainservice.repository.ChannelConfigChangeRepository;
import com.paytech.channel.connector.domainservice.service.ChannelConfigChangeQueryService;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

import lombok.AllArgsConstructor;

/** 变更记录只读查询；对象不存在与没有变更记录同样返回空页，不额外核对对象存在性。 */
@Service
@AllArgsConstructor
public class ChannelConfigChangeQueryServiceImpl implements ChannelConfigChangeQueryService {
    private final ChannelConfigChangeRepository channelConfigChangeRepository;

    /** 委托仓储按对象分页读取。 */
    @Override
    public PagingResponseBody<ChannelConfigChange> listChanges(ChangeTargetType targetType, long targetId,
        PagingRequestBody paging) {
        return channelConfigChangeRepository.findPage(targetType, targetId, paging);
    }
}
