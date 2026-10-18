package com.paytech.card.ext.rest;

import java.time.Duration;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.card.api.enums.CardMaterialPurpose;
import com.paytech.card.api.ticket.CardMaterialTicketApi;
import com.paytech.card.api.ticket.request.IssueCardMaterialTicketRequestBody;
import com.paytech.card.api.ticket.response.IssueCardMaterialTicketResponseBody;
import com.paytech.card.domain.IssuedCardMaterialTicket;
import com.paytech.card.service.processor.IssueCardMaterialTicketProcessor;
import com.paytech.card.service.processor.request.IssueCardMaterialTicketRequest;
import com.paytech.foundation.common.utils.EnumUtils;

import lombok.AllArgsConstructor;

/** 一次性读卡票据签发私网入口；响应含票据原值，访问日志按全局配置只记元数据。 */
@RestController
@AllArgsConstructor
public class CardMaterialTicketController implements CardMaterialTicketApi {
    final CardCallerResolver callers;
    final IssueCardMaterialTicketProcessor processor;

    /** 签发读卡票据；用途已由 Body 注解限定为 CHANNEL_EXECUTION。 */
    @Override
    public IssueCardMaterialTicketResponseBody issueTicket(String caller, IssueCardMaterialTicketRequestBody body) {
        IssuedCardMaterialTicket issued = processor.process(new IssueCardMaterialTicketRequest(callers.resolve(caller),
            body.getInstrumentRef(), Long.valueOf(body.getPaymentAttemptId()), Long.valueOf(body.getPaymentActionId()),
            EnumUtils.getEnumByCode(CardMaterialPurpose.class, body.getPurpose()),
            Duration.ofSeconds(body.getTtlSeconds())));
        var response = new IssueCardMaterialTicketResponseBody();
        response.setTicket(issued.ticket());
        response.setExpiresAt(issued.expiresAt().toString());
        return response;
    }
}
