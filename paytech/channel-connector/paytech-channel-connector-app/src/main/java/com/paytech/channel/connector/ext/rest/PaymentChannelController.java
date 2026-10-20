package com.paytech.channel.connector.ext.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.channel.connector.api.channel.PaymentChannelApi;
import com.paytech.channel.connector.api.channel.request.ChangeChannelStatusRequestBody;
import com.paytech.channel.connector.api.channel.request.CreateChannelRequestBody;
import com.paytech.channel.connector.api.channel.request.UpdateChannelRequestBody;
import com.paytech.channel.connector.api.channel.response.ChannelResponseBody;
import com.paytech.channel.connector.api.channel.response.ChannelSummaryResponseBody;
import com.paytech.channel.connector.domainservice.service.PaymentChannelService;
import com.paytech.channel.connector.ext.rest.converter.PaymentChannelConverter;
import com.paytech.channel.connector.service.processor.ChangeChannelStatusProcessor;
import com.paytech.channel.connector.service.processor.CreateChannelProcessor;
import com.paytech.channel.connector.service.processor.UpdateChannelProcessor;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

import lombok.AllArgsConstructor;

/** 支付渠道管理的 HTTP 协议适配：写入口委托 Processor，查询直接委托本地 Service。 */
@RestController
@AllArgsConstructor
public class PaymentChannelController implements PaymentChannelApi {
    private final PaymentChannelConverter converter;
    private final CreateChannelProcessor createChannelProcessor;
    private final UpdateChannelProcessor updateChannelProcessor;
    private final PaymentChannelService paymentChannelService;
    private final ChangeChannelStatusProcessor changeChannelStatusProcessor;

    /** 创建渠道并返回 201。 */
    @Override
    public ResponseEntity<ChannelResponseBody> createChannel(String actorId, CreateChannelRequestBody body) {
        ChannelResponseBody response =
            converter.toResponseBody(createChannelProcessor.process(converter.toRequest(actorId, body)));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** 分页查询渠道。 */
    @Override
    public PagingResponseBody<ChannelSummaryResponseBody> listChannels(String status, String keyword, Integer page,
        Integer size) {
        return paymentChannelService.listChannels(converter.status(status), keyword, PagingRequestBody.of(page, size))
            .map(converter::toSummaryResponseBody);
    }

    /** 渠道详情。 */
    @Override
    public ChannelResponseBody getChannel(String channelId) {
        return converter.toResponseBody(paymentChannelService.loadChannel(converter.channelId(channelId)));
    }

    /** 修改渠道名称。 */
    @Override
    public ChannelResponseBody updateChannel(String actorId, String channelId, UpdateChannelRequestBody body) {
        return converter.toResponseBody(updateChannelProcessor.process(converter.toRequest(actorId, channelId, body)));
    }

    /** 变更渠道状态。 */
    @Override
    public ChannelResponseBody changeChannelStatus(String actorId, String channelId,
        ChangeChannelStatusRequestBody body) {
        return converter.toResponseBody(
            changeChannelStatusProcessor.process(converter.toStatusRequest(actorId, channelId, body)));
    }
}
