package com.paytech.payment.execution.integration.impl;

import java.time.Instant;

import org.springframework.stereotype.Component;

import com.paytech.card.api.CardApiConstants;
import com.paytech.card.api.enums.CardMaterialPurpose;
import com.paytech.card.api.instrument.CardInstrumentApi;
import com.paytech.card.api.instrument.request.CreateCardInstrumentRequestBody;
import com.paytech.card.api.instrument.response.CreateCardInstrumentResponseBody;
import com.paytech.card.api.ticket.CardMaterialTicketApi;
import com.paytech.card.api.ticket.request.IssueCardMaterialTicketRequestBody;
import com.paytech.card.api.ticket.response.IssueCardMaterialTicketResponseBody;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.payment.execution.domain.CardInput;
import com.paytech.payment.execution.domain.CardInstrumentCommand;
import com.paytech.payment.execution.domain.CardInstrumentResult;
import com.paytech.payment.execution.integration.PaymentCardClient;

import lombok.AllArgsConstructor;

/**
 * card 端口的 HTTP 实现：只做合同 DTO 与领域模型的转换，校验响应必填字段；不记录卡值、票据或卡指纹。
 */
@Component
@AllArgsConstructor
public class PaymentCardClientImpl implements PaymentCardClient {
    /** 读卡票据有效秒数：覆盖一次渠道调用中 Platform 初始化读卡的时间窗。 */
    static final int TICKET_TTL_SECONDS = 90;

    private static final String CALLER = CardApiConstants.PAYMENT_SERVICE;

    private final CardInstrumentApi cardInstrumentApi;
    private final CardMaterialTicketApi cardMaterialTicketApi;

    @Override
    public CardInstrumentResult createInstrument(CardInstrumentCommand command) {
        CreateCardInstrumentResponseBody body = cardInstrumentApi.createInstrument(CALLER, request(command));
        if (body == null || body.getInstrumentRef() == null || !body.getInstrumentRef().matches("[1-9][0-9]{0,18}")
            || body.getExpiresAt() == null || body.getRiskAttributes() == null
            || body.getRiskAttributes().getBin() == null || !body.getRiskAttributes().getBin().matches("[0-9]{6,8}")
            || body.getRiskAttributes().getFingerprint() == null
            || !body.getRiskAttributes().getFingerprint().matches("[0-9a-f]{64}")) {
            throw new ErrorException("Card 工具创建响应无效");
        }
        return new CardInstrumentResult(body.getInstrumentRef(), instant(body.getExpiresAt()),
            body.getRiskAttributes().getBin(), body.getRiskAttributes().getFingerprint());
    }

    @Override
    public String issueMaterialTicket(String instrumentRef, long attemptId, long actionId) {
        var request = new IssueCardMaterialTicketRequestBody();
        request.setInstrumentRef(instrumentRef);
        request.setPaymentAttemptId(Long.toString(attemptId));
        request.setPaymentActionId(Long.toString(actionId));
        request.setPurpose(CardMaterialPurpose.CHANNEL_EXECUTION.getCode());
        request.setTtlSeconds(TICKET_TTL_SECONDS);
        IssueCardMaterialTicketResponseBody body = cardMaterialTicketApi.issueTicket(CALLER, request);
        if (body == null || body.getTicket() == null || !body.getTicket().matches("[A-Za-z0-9_-]{43}")) {
            throw new ErrorException("Card 读卡票据响应无效");
        }
        return body.getTicket();
    }

    /** 合同请求：ID 为十进制字符串，时间为 ISO-8601 UTC。 */
    static CreateCardInstrumentRequestBody request(CardInstrumentCommand command) {
        var request = new CreateCardInstrumentRequestBody();
        request.setClientId(command.getClientId().toString());
        request.setCustomerId(command.getCustomerId() == null ? null : command.getCustomerId().toString());
        request.setSourceSubmissionId(command.getSourceSubmissionId().toString());
        request.setConsentToSave(command.isConsentToSave());
        request.setExpiresAt(command.getExpiresAt().toString());
        CardInput input = command.getCard();
        if (command.getStoredCardToken() != null) {
            var storedCard = new CreateCardInstrumentRequestBody.StoredCard();
            storedCard.setStoredCardToken(command.getStoredCardToken());
            storedCard.setCvv(input.getCvv());
            request.setStoredCard(storedCard);
            return request;
        }
        var card = new CreateCardInstrumentRequestBody.Card();
        card.setPan(input.getPan());
        card.setExpiryMonth(input.getExpiryMonth());
        card.setExpiryYear(input.getExpiryYear());
        card.setCardholderName(input.getCardholderName());
        card.setCvv(input.getCvv());
        request.setCard(card);
        return request;
    }

    /** 无效时间视为响应无效。 */
    static Instant instant(String value) {
        try {
            return Instant.parse(value);
        } catch (java.time.DateTimeException invalid) {
            throw new ErrorException("Card 工具期限无效");
        }
    }
}
