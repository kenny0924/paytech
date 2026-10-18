package com.paytech.channel.connector.ext.rest;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.channel.connector.api.operation.ChannelOperationApi;
import com.paytech.channel.connector.api.operation.request.ExecuteChannelOperationRequestBody;
import com.paytech.channel.connector.api.operation.response.ChannelOperationResponseBody;
import com.paytech.channel.connector.ext.rest.converter.ExecuteChannelOperationConverter;
import com.paytech.channel.connector.service.processor.ExecuteChannelOperationProcessor;

import lombok.AllArgsConstructor;

/** 渠道操作执行入口：协议适配后委托 Processor。 */
@RestController
@AllArgsConstructor
public class ChannelOperationController implements ChannelOperationApi {
    final ExecuteChannelOperationConverter executeChannelOperationConverter;
    final ExecuteChannelOperationProcessor executeChannelOperationProcessor;

    /** 执行一次渠道操作。 */
    @Override
    public ChannelOperationResponseBody execute(ExecuteChannelOperationRequestBody requestBody) {
        var result = executeChannelOperationProcessor.process(executeChannelOperationConverter.convertRequest(requestBody));
        return executeChannelOperationConverter.convertResponse(result);
    }
}
