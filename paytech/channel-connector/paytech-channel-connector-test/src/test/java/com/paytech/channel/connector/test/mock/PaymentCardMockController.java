package com.paytech.channel.connector.test.mock;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.paytech.card.api.material.ChannelCardMaterialApi;
import com.paytech.card.api.material.request.RevealChannelCardMaterialRequestBody;
import com.paytech.card.api.material.response.RevealChannelCardMaterialResponseBody;
import com.paytech.channel.connector.test.mocker.PaymentCardMocker;

import lombok.RequiredArgsConstructor;

/** 独立 Mock HTTP 服务以 card-service 实现正式读卡 Wire，委托 PaymentCardMocker；不装入被测应用路由；另记录出站内部 Token 头。 */
@RestController
@TestComponent
@RequiredArgsConstructor
public class PaymentCardMockController implements ChannelCardMaterialApi {

    private final PaymentCardMocker cards;

    /** 记录 Authorization 头后委托 Mocker 返回登记的合成卡。 */
    @Override
    public RevealChannelCardMaterialResponseBody reveal(String caller, RevealChannelCardMaterialRequestBody body) {
        var request = ((ServletRequestAttributes)RequestContextHolder.currentRequestAttributes()).getRequest();
        cards.recordAuthorization(request.getHeader(HttpHeaders.AUTHORIZATION));
        return cards.reveal(caller, body);
    }
}
