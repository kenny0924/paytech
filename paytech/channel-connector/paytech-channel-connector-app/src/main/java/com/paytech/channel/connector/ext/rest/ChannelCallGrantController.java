package com.paytech.channel.connector.ext.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.channel.connector.api.grant.ChannelCallGrantApi;
import com.paytech.channel.connector.api.grant.request.ChangeChannelCallGrantStatusRequestBody;
import com.paytech.channel.connector.api.grant.request.CreateChannelCallGrantRequestBody;
import com.paytech.channel.connector.api.grant.response.ChannelCallGrantResponseBody;
import com.paytech.channel.connector.ext.rest.converter.ChannelCallGrantConverter;
import com.paytech.channel.connector.protocol.domainservice.service.ChannelCallGrantService;
import com.paytech.channel.connector.protocol.service.processor.ChangeChannelCallGrantStatusProcessor;
import com.paytech.channel.connector.protocol.service.processor.CreateChannelCallGrantProcessor;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

import lombok.AllArgsConstructor;

/** 服务渠道调用授权的 HTTP 协议适配：写入口委托 protocol 的 Processor，分页查询直接委托授权服务。 */
@RestController
@AllArgsConstructor
public class ChannelCallGrantController implements ChannelCallGrantApi {
    private final ChannelCallGrantConverter converter;
    private final ChannelCallGrantService channelCallGrantService;
    private final CreateChannelCallGrantProcessor createChannelCallGrantProcessor;
    private final ChangeChannelCallGrantStatusProcessor changeChannelCallGrantStatusProcessor;

    /** 分页查询授权。 */
    @Override
    public PagingResponseBody<ChannelCallGrantResponseBody> listChannelCallGrants(String channelId,
        String callerService, String standardApiCode, String grantStatus, Integer page, Integer size) {
        return channelCallGrantService.listGrants(converter.channelId(channelId),
            converter.callerService(callerService), converter.standardApiCode(standardApiCode),
            converter.grantStatus(grantStatus), PagingRequestBody.of(page, size)).map(converter::toResponseBody);
    }

    /** 新建授权并返回 201。 */
    @Override
    public ResponseEntity<ChannelCallGrantResponseBody> createChannelCallGrant(String actorId,
        CreateChannelCallGrantRequestBody body) {
        ChannelCallGrantResponseBody response =
            converter.toResponseBody(createChannelCallGrantProcessor.process(converter.toRequest(actorId, body)));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** 启用或停用授权。 */
    @Override
    public ChannelCallGrantResponseBody changeChannelCallGrantStatus(String actorId, String callGrantId,
        ChangeChannelCallGrantStatusRequestBody body) {
        return converter.toResponseBody(
            changeChannelCallGrantStatusProcessor.process(converter.toRequest(actorId, callGrantId, body)));
    }
}
