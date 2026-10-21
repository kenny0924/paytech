package com.paytech.channel.connector.ext.rest;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.channel.connector.api.change.ChannelConfigChangeApi;
import com.paytech.channel.connector.api.change.response.ChannelConfigChangeResponseBody;
import com.paytech.channel.connector.ext.rest.converter.ChannelConfigChangeConverter;
import com.paytech.channel.connector.protocol.domainservice.service.ChannelConfigChangeService;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

import lombok.AllArgsConstructor;

/** 变更记录查询的 HTTP 协议适配：直接委托 protocol 公开的变更记录 Service，无 Processor、无事务。 */
@RestController
@AllArgsConstructor
public class ChannelConfigChangeController implements ChannelConfigChangeApi {
    private final ChannelConfigChangeConverter converter;
    private final ChannelConfigChangeService channelConfigChangeService;

    /** 分页查询一个对象的变更记录。 */
    @Override
    public PagingResponseBody<ChannelConfigChangeResponseBody> listConfigChanges(String targetType, String targetId,
        Integer page, Integer size) {
        return channelConfigChangeService.listChanges(converter.targetType(targetType), Long.parseLong(targetId),
            PagingRequestBody.of(page, size)).map(converter::toResponseBody);
    }
}
