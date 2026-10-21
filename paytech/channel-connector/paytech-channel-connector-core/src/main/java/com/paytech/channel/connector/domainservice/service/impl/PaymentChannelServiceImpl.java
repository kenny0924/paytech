package com.paytech.channel.connector.domainservice.service.impl;

import org.springframework.stereotype.Service;

import com.paytech.channel.connector.core.dal.dataobject.PaymentChannel;
import com.paytech.channel.connector.core.enums.ChannelStatus;
import com.paytech.channel.connector.domain.ChannelContext;
import com.paytech.channel.connector.domain.ChannelSummaryView;
import com.paytech.channel.connector.domainservice.repository.PaymentChannelRepository;
import com.paytech.channel.connector.domainservice.service.PaymentChannelService;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

import lombok.AllArgsConstructor;

/** 支付渠道的本地保存与查询；写入方法加入调用方 Processor 的事务，变更记录由 Processor 在同一事务中写入。 */
@Service
@AllArgsConstructor
public class PaymentChannelServiceImpl implements PaymentChannelService {
    private final PaymentChannelRepository paymentChannelRepository;

    /** 读取渠道；不存在时业务拒绝。 */
    @Override
    public PaymentChannel loadChannel(long channelId) {
        PaymentChannel channel = paymentChannelRepository.findById(channelId);
        if (channel == null) {
            throw new BizCheckFailException("渠道不存在: " + channelId);
        }
        return channel;
    }

    /** 分页查询渠道列表项。 */
    @Override
    public PagingResponseBody<ChannelSummaryView> listChannels(ChannelStatus status, String keyword,
        PagingRequestBody paging) {
        return paymentChannelRepository.findSummaries(status, keyword, paging);
    }

    /** 写入新渠道；须在调用方事务内执行。 */
    @Override
    public void createChannel(ChannelContext context) {
        paymentChannelRepository.insert(context.getChannel());
    }

    /** 改名；须在调用方事务内执行。 */
    @Override
    public void renameChannel(ChannelContext context, String channelName) {
        paymentChannelRepository.updateName(context.getChannel(), channelName, context.getNow());
    }

    /** 变更状态；须在调用方事务内执行。 */
    @Override
    public void changeChannelStatus(ChannelContext context, ChannelStatus targetStatus) {
        paymentChannelRepository.updateStatus(context.getChannel(), targetStatus, context.getNow());
    }
}
