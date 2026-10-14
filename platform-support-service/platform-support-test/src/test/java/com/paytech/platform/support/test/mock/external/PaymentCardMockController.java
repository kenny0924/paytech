package com.paytech.platform.support.test.mock.external;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.payment.api.card.ChannelCardMaterialApi;
import com.paytech.payment.api.card.request.RevealChannelCardMaterialRequestBody;
import com.paytech.payment.api.card.response.RevealChannelCardMaterialResponseBody;
import com.paytech.platform.support.test.mocker.external.PaymentCardMocker;

import lombok.RequiredArgsConstructor;

/** 独立 Mock HTTP 服务实现正式 Payment 读卡 Wire，委托 PaymentCardMocker；不装入 Platform 路由。 */
@RestController
@TestComponent
@RequiredArgsConstructor
public class PaymentCardMockController implements ChannelCardMaterialApi {

    private final PaymentCardMocker cards;

    @Override
    public RevealChannelCardMaterialResponseBody reveal(String caller, RevealChannelCardMaterialRequestBody body) {
        return cards.reveal(caller, body);
    }
}
