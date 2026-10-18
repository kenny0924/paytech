package com.paytech.payment.test.mock;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.paytech.card.api.instrument.CardInstrumentApi;
import com.paytech.card.api.instrument.request.CreateCardInstrumentRequestBody;
import com.paytech.card.api.instrument.response.CreateCardInstrumentResponseBody;
import com.paytech.card.api.material.ChannelCardMaterialApi;
import com.paytech.card.api.material.request.RevealChannelCardMaterialRequestBody;
import com.paytech.card.api.material.response.RevealChannelCardMaterialResponseBody;
import com.paytech.card.api.ticket.CardMaterialTicketApi;
import com.paytech.card.api.ticket.request.IssueCardMaterialTicketRequestBody;
import com.paytech.card.api.ticket.response.IssueCardMaterialTicketResponseBody;
import com.paytech.payment.test.mocker.CardApiMocker;

import lombok.AllArgsConstructor;

/** 仅存在于独立测试 HTTP 服务的 card-api 合同实现（服务发现名 card-service，无 context path）。 */
@RestController
@TestComponent
@AllArgsConstructor
public class MockCardController implements CardInstrumentApi, CardMaterialTicketApi, ChannelCardMaterialApi {
    private final CardApiMocker cards;

    /** 委托 CardApiMocker 创建或重放工具。 */
    @Override
    public CreateCardInstrumentResponseBody createInstrument(String caller, CreateCardInstrumentRequestBody body) {
        requestId();
        return cards.createInstrument(caller, body);
    }

    /** 委托 CardApiMocker 签发票据。 */
    @Override
    public IssueCardMaterialTicketResponseBody issueTicket(String caller, IssueCardMaterialTicketRequestBody body) {
        requestId();
        return cards.issueTicket(caller, body);
    }

    /** 委托 CardApiMocker 凭票据读卡（由 PlatformMocker 以 Platform 身份调用）。 */
    @Override
    public RevealChannelCardMaterialResponseBody reveal(String caller, RevealChannelCardMaterialRequestBody body) {
        requestId();
        return cards.reveal(caller, body.getTicket());
    }

    /** 与故障 Problem 中的 requestId 一致，供公共解码核对。 */
    static void requestId() {
        var attributes = (ServletRequestAttributes)RequestContextHolder.currentRequestAttributes();
        attributes.getResponse().setHeader("X-Request-Id", CardApiMocker.ERROR_REQUEST_ID);
    }
}
