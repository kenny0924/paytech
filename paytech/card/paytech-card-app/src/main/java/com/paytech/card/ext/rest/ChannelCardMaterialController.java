package com.paytech.card.ext.rest;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.card.api.material.ChannelCardMaterialApi;
import com.paytech.card.api.material.request.RevealChannelCardMaterialRequestBody;
import com.paytech.card.api.material.response.RevealChannelCardMaterialResponseBody;
import com.paytech.card.domain.ChannelCardMaterial;
import com.paytech.card.service.processor.RevealChannelCardMaterialProcessor;
import com.paytech.card.service.processor.request.RevealCardMaterialRequest;

import lombok.AllArgsConstructor;

/** 凭票据读卡私网入口（自 Payment 迁入，design/05 §2.1）；响应含卡原值，访问日志按全局配置只记元数据。 */
@RestController
@AllArgsConstructor
public class ChannelCardMaterialController implements ChannelCardMaterialApi {
    final CardCallerResolver callers;
    final RevealChannelCardMaterialProcessor processor;

    /** 读取渠道执行所需的卡数据 */
    @Override
    public RevealChannelCardMaterialResponseBody reveal(String caller, RevealChannelCardMaterialRequestBody body) {
        ChannelCardMaterial material =
            processor.process(new RevealCardMaterialRequest(callers.resolve(caller), body.getTicket()));
        var response = new RevealChannelCardMaterialResponseBody();
        response.setPan(material.getPan());
        response.setExpiryMonth(material.getExpiryMonth());
        response.setExpiryYear(material.getExpiryYear());
        response.setCardholderName(material.getCardholderName());
        response.setCvv(material.getCvv());
        return response;
    }
}
