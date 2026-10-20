package com.paytech.channel.connector.domainservice.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.channel.connector.core.dal.dataobject.PaymentChannel;
import com.paytech.channel.connector.core.enums.ChannelStatus;
import com.paytech.channel.connector.domain.ChannelContext;
import com.paytech.channel.connector.domain.ChannelSummaryView;
import com.paytech.channel.connector.domainservice.repository.ChannelConfigChangeRepository;
import com.paytech.channel.connector.domainservice.repository.PaymentChannelRepository;
import com.paytech.channel.connector.domainservice.service.PaymentChannelService;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

import lombok.AllArgsConstructor;

/** 支付渠道的本地保存与查询；写入与变更记录同事务提交。 */
@Service
@AllArgsConstructor
public class PaymentChannelServiceImpl implements PaymentChannelService {
    private final TransactionTemplate transactionTemplate;
    private final PaymentChannelRepository paymentChannelRepository;
    private final ChannelConfigChangeRepository channelConfigChangeRepository;

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

    /** 写入新渠道与变更记录。 */
    @Override
    public void createChannel(ChannelContext context) {
        transactionTemplate.executeWithoutResult(status -> {
            paymentChannelRepository.insert(context.getChannel());
            channelConfigChangeRepository.insert(context.getChange());
        });
    }

    /** 改名并写入变更记录。 */
    @Override
    public void renameChannel(ChannelContext context, String channelName) {
        transactionTemplate.executeWithoutResult(status -> {
            paymentChannelRepository.updateName(context.getChannel(), channelName, context.getNow());
            channelConfigChangeRepository.insert(context.getChange());
        });
    }

    /** 变更状态并写入变更记录。 */
    @Override
    public void changeChannelStatus(ChannelContext context, ChannelStatus targetStatus) {
        transactionTemplate.executeWithoutResult(status -> {
            paymentChannelRepository.updateStatus(context.getChannel(), targetStatus, context.getNow());
            channelConfigChangeRepository.insert(context.getChange());
        });
    }
}
