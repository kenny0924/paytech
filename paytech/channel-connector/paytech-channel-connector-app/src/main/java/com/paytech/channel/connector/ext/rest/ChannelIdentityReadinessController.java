package com.paytech.channel.connector.ext.rest;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.channel.connector.api.readiness.ChannelIdentityReadinessApi;
import com.paytech.channel.connector.api.readiness.request.ChannelIdentityReadinessRequestBody;
import com.paytech.channel.connector.api.readiness.response.ChannelIdentityReadinessResponseBody;
import com.paytech.channel.connector.ext.rest.converter.ChannelIdentityReadinessConverter;
import com.paytech.channel.connector.service.processor.QueryChannelIdentityReadinessProcessor;

import lombok.AllArgsConstructor;

/** 渠道身份就绪查询入口：协议适配后委托 Processor。 */
@RestController
@AllArgsConstructor
public class ChannelIdentityReadinessController implements ChannelIdentityReadinessApi {
    final ChannelIdentityReadinessConverter channelIdentityReadinessConverter;
    final QueryChannelIdentityReadinessProcessor queryChannelIdentityReadinessProcessor;

    /** 判定渠道身份是否就绪。 */
    @Override
    public ChannelIdentityReadinessResponseBody checkReadiness(ChannelIdentityReadinessRequestBody requestBody) {
        var results = queryChannelIdentityReadinessProcessor.process(
            channelIdentityReadinessConverter.convertRequest(requestBody));
        return channelIdentityReadinessConverter.convertResponse(requestBody.getEntityNo(), results);
    }
}
