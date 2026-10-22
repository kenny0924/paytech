package com.paytech.channel.connector.protocol.domainservice.service.impl;

import org.springframework.stereotype.Service;

import com.paytech.channel.connector.api.enums.ChannelCallGrantStatus;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelCallGrant;
import com.paytech.channel.connector.protocol.domain.ChangeChannelCallGrantStatusContext;
import com.paytech.channel.connector.protocol.domain.ChannelCallGrantView;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelCallGrantRepository;
import com.paytech.channel.connector.protocol.domainservice.service.ChannelCallGrantService;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

import lombok.AllArgsConstructor;

/** 调用授权的本地查询与保存；写入方法加入调用方 Processor 的事务。 */
@Service
@AllArgsConstructor
public class ChannelCallGrantServiceImpl implements ChannelCallGrantService {
    private final ChannelCallGrantRepository channelCallGrantRepository;

    /** 是否有 ACTIVE 授权；普通一致性读，不加锁。 */
    @Override
    public boolean hasActiveGrant(String callerService, long channelId, String standardApiCode) {
        return channelCallGrantRepository.hasActive(callerService, channelId, standardApiCode);
    }

    /** 分页查询授权。 */
    @Override
    public PagingResponseBody<ChannelCallGrantView> listGrants(Long channelId, String callerService,
        String standardApiCode, ChannelCallGrantStatus grantStatus, PagingRequestBody paging) {
        return channelCallGrantRepository.findViews(channelId, callerService, standardApiCode, grantStatus, paging);
    }

    /** 插入新授权；须在调用方事务内执行。 */
    @Override
    public void createGrant(ChannelCallGrant grant) {
        channelCallGrantRepository.insert(grant);
    }

    /** 启停授权；须在调用方事务内执行。 */
    @Override
    public void changeGrantStatus(ChangeChannelCallGrantStatusContext context) {
        channelCallGrantRepository.updateStatus(context.getGrant(), context.getTargetStatus(), context.getActorId(),
            context.getNow());
    }
}
