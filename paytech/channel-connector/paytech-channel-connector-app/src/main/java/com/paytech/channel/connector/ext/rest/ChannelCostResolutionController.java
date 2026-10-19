package com.paytech.channel.connector.ext.rest;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.channel.connector.api.cost.ChannelCostResolutionApi;
import com.paytech.channel.connector.api.cost.request.ResolveChannelCostRequestBody;
import com.paytech.channel.connector.api.cost.response.ChannelCostResolutionResponseBody;
import com.paytech.channel.connector.ext.rest.converter.ChannelCostPlanConverter;
import com.paytech.channel.connector.service.processor.ResolveChannelCostProcessor;

import lombok.AllArgsConstructor;

/** 渠道成本命中的 HTTP 协议适配：委托只读 Processor。 */
@RestController
@AllArgsConstructor
public class ChannelCostResolutionController implements ChannelCostResolutionApi {
    private final ChannelCostPlanConverter converter;
    private final ResolveChannelCostProcessor resolveChannelCostProcessor;

    /** 命中渠道成本范围与组成。 */
    @Override
    public ChannelCostResolutionResponseBody resolveChannelCost(ResolveChannelCostRequestBody body) {
        return converter.toResponseBody(resolveChannelCostProcessor.process(converter.toRequest(body)));
    }
}
