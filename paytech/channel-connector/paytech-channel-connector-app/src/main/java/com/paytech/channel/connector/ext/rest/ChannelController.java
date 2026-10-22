package com.paytech.channel.connector.ext.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.channel.connector.api.channel.ChannelApi;
import com.paytech.channel.connector.api.channel.request.ChangeChannelStatusRequestBody;
import com.paytech.channel.connector.api.channel.request.CreateChannelRequestBody;
import com.paytech.channel.connector.api.channel.request.UpdateChannelEndpointsRequestBody;
import com.paytech.channel.connector.api.channel.request.UpdateChannelRequestBody;
import com.paytech.channel.connector.api.channel.response.ChannelResponseBody;
import com.paytech.channel.connector.api.channel.response.ChannelSummaryResponseBody;
import com.paytech.channel.connector.ext.rest.converter.ChannelConverter;
import com.paytech.channel.connector.protocol.domainservice.service.ChannelService;
import com.paytech.channel.connector.protocol.service.processor.CreateChannelProcessor;
import com.paytech.channel.connector.protocol.service.processor.UpdateChannelEndpointsProcessor;
import com.paytech.channel.connector.protocol.service.processor.UpdateChannelProcessor;
import com.paytech.channel.connector.service.processor.ChangeChannelStatusProcessor;
import com.paytech.channel.connector.service.processor.ListChannelsProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;

import lombok.AllArgsConstructor;

/**
 * 渠道管理的 HTTP 协议适配：渠道身份的创建、名称与出站地址修改委托 protocol 的 Processor，状态变更与列表委托 core 的 Processor
 * （涉及账号与 MID），详情直接委托 protocol 的渠道服务。
 */
@RestController
@AllArgsConstructor
public class ChannelController implements ChannelApi {
    private final ChannelService channelService;
    private final ChannelConverter converter;
    private final ListChannelsProcessor listChannelsProcessor;
    private final CreateChannelProcessor createChannelProcessor;
    private final UpdateChannelProcessor updateChannelProcessor;
    private final ChangeChannelStatusProcessor changeChannelStatusProcessor;
    private final UpdateChannelEndpointsProcessor updateChannelEndpointsProcessor;

    /** 创建渠道并返回 201。 */
    @Override
    public ResponseEntity<ChannelResponseBody> createChannel(String actorId, CreateChannelRequestBody body) {
        ChannelResponseBody response =
            converter.toResponseBody(createChannelProcessor.process(converter.toRequest(actorId, body)));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** 分页查询渠道。 */
    @Override
    public PagingResponseBody<ChannelSummaryResponseBody> listChannels(String channelType, String status,
        String keyword, Integer page, Integer size) {
        return listChannelsProcessor.process(converter.toListRequest(channelType, status, keyword, page, size))
            .map(converter::toSummaryResponseBody);
    }

    /** 渠道详情。 */
    @Override
    public ChannelResponseBody getChannel(String channelId) {
        return converter.toResponseBody(channelService.loadChannel(converter.channelId(channelId)));
    }

    /** 修改渠道名称与说明。 */
    @Override
    public ChannelResponseBody updateChannel(String actorId, String channelId, UpdateChannelRequestBody body) {
        return converter.toResponseBody(updateChannelProcessor.process(converter.toRequest(actorId, channelId, body)));
    }

    /** 整组替换渠道出站地址。 */
    @Override
    public ChannelResponseBody updateChannelEndpoints(String actorId, String channelId,
        UpdateChannelEndpointsRequestBody body) {
        return converter.toResponseBody(
            updateChannelEndpointsProcessor.process(converter.toRequest(actorId, channelId, body)));
    }

    /** 变更渠道状态。 */
    @Override
    public ChannelResponseBody changeChannelStatus(String actorId, String channelId,
        ChangeChannelStatusRequestBody body) {
        return converter.toResponseBody(
            changeChannelStatusProcessor.process(converter.toStatusRequest(actorId, channelId, body)));
    }
}
