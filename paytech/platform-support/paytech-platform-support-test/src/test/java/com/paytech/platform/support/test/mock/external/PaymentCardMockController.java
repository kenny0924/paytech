package com.paytech.platform.support.test.mock.external;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.paytech.payment.api.card.ChannelCardMaterialApi;
import com.paytech.payment.api.card.request.RevealChannelCardMaterialRequestBody;
import com.paytech.payment.api.card.response.RevealChannelCardMaterialResponseBody;
import com.paytech.platform.support.test.mocker.external.PaymentCardMocker;

import lombok.RequiredArgsConstructor;

/** 独立 Mock HTTP 服务实现正式 Payment 读卡 Wire，委托 PaymentCardMocker；不装入 Platform 路由；另记录出站内部 Token 头。 */
@RestController
@TestComponent
@RequiredArgsConstructor
public class PaymentCardMockController implements ChannelCardMaterialApi {

    private final PaymentCardMocker cards;

    /** 记录 Authorization 头后委托 Mocker 返回登记的合成卡。 */
    @Override
    public RevealChannelCardMaterialResponseBody reveal(String caller, RevealChannelCardMaterialRequestBody body) {
        var request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        cards.recordAuthorization(request.getHeader(HttpHeaders.AUTHORIZATION));
        return cards.reveal(caller, body);
    }
}
