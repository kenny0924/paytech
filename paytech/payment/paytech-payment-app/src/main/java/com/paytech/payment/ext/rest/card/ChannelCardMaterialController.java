package com.paytech.payment.ext.rest.card;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.payment.api.card.ChannelCardMaterialApi;
import com.paytech.payment.api.card.request.RevealChannelCardMaterialRequestBody;
import com.paytech.payment.api.card.response.RevealChannelCardMaterialResponseBody;
import com.paytech.payment.execution.service.processor.RevealChannelCardMaterialProcessor;
import com.paytech.payment.ext.rest.converter.RevealChannelCardMaterialConverter;

import lombok.AllArgsConstructor;

/** 渠道执行读卡私网入口；响应含卡原值，访问日志按全局配置只记元数据。 */
@RestController
@AllArgsConstructor
public class ChannelCardMaterialController implements ChannelCardMaterialApi {
    final RevealChannelCardMaterialConverter converter;
    final RevealChannelCardMaterialProcessor processor;

    /** 读取渠道执行所需的卡数据 */
    @Override
    public RevealChannelCardMaterialResponseBody reveal(String caller, RevealChannelCardMaterialRequestBody body) {
        return converter.convertResponse(processor.process(converter.convertRequest(caller, body)));
    }
}
