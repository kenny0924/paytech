package com.paytech.payment.ext.rest.converter;

import org.springframework.stereotype.Component;

import com.paytech.payment.api.card.request.RevealChannelCardMaterialRequestBody;
import com.paytech.payment.api.card.response.RevealChannelCardMaterialResponseBody;
import com.paytech.payment.card.domain.ChannelCardMaterial;
import com.paytech.payment.execution.service.processor.request.RevealChannelCardMaterialRequest;

/** 渠道执行读卡协议转换；不记录、不缓存卡值。 */
@Component
public class RevealChannelCardMaterialConverter {

    /** 转换请求，支付尝试 ID 已由 Body 注解校验为正十进制 long。 */
    public RevealChannelCardMaterialRequest convertRequest(String caller, RevealChannelCardMaterialRequestBody body) {
        var request = new RevealChannelCardMaterialRequest();
        request.setCaller(caller);
        request.setPaymentInstrumentToken(body.getPaymentInstrumentToken());
        request.setPaymentAttemptId(Long.valueOf(body.getPaymentAttemptId()));
        request.setPurpose(body.getPurpose());
        return request;
    }

    /** 转换响应。 */
    public RevealChannelCardMaterialResponseBody convertResponse(ChannelCardMaterial material) {
        var body = new RevealChannelCardMaterialResponseBody();
        body.setPan(material.getPan());
        body.setExpiryMonth(material.getExpiryMonth());
        body.setExpiryYear(material.getExpiryYear());
        body.setCardholderName(material.getCardholderName());
        body.setCvv(material.getCvv());
        return body;
    }
}
