package com.paytech.payment.execution.integration.impl;

import java.time.Instant;

import org.springframework.stereotype.Component;

import com.paytech.card.api.CardApiConstants;
import com.paytech.card.api.enums.CardBrand;
import com.paytech.card.api.enums.CardFundingType;
import com.paytech.card.api.enums.CardMaterialPurpose;
import com.paytech.card.api.enums.CardProduct;
import com.paytech.card.api.instrument.CardInstrumentApi;
import com.paytech.card.api.instrument.request.CreateCardInstrumentRequestBody;
import com.paytech.card.api.instrument.response.CreateCardInstrumentResponseBody;
import com.paytech.card.api.ticket.CardMaterialTicketApi;
import com.paytech.card.api.ticket.request.IssueCardMaterialTicketRequestBody;
import com.paytech.card.api.ticket.response.IssueCardMaterialTicketResponseBody;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.utils.EnumUtils;
import com.paytech.payment.execution.domain.CardInput;
import com.paytech.payment.execution.domain.CardInstrumentCommand;
import com.paytech.payment.execution.domain.CardInstrumentResult;
import com.paytech.payment.execution.integration.PaymentCardClient;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * card 端口的 HTTP 实现：只做合同 DTO 与领域模型的转换，校验响应必填字段；不记录卡值、票据或卡指纹。
 */
@Slf4j
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
            body.getRiskAttributes().getBin(), body.getRiskAttributes().getFingerprint(),
            cardAttributes(body.getInstrumentRef(), body.getCardAttributes()));
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

    /**
     * BIN 库卡属性快照：识别时卡组织、发卡国与发卡区域必须有值，未识别时各属性必须为空
     * <li>缺少快照对象（BIN 库上线前的旧 Card）按未查询处理：8 列全空并记 WARN，使 Payment 可先于 Card 发布。</li>
     * <li>对象存在时闭合代码按 card 发布的枚举校验，未知 Code 失败关闭；违反 Card 合同时抛出系统异常，不保存 Token。</li>
     *
     * @param instrumentRef Card 工具引用，只用于日志定位
     * @param body Card 响应中的快照，可能缺失
     * @return 待冻结的快照
     */
    static CardInstrumentResult.CardAttributes cardAttributes(String instrumentRef,
        CreateCardInstrumentResponseBody.CardAttributes body) {
        if (body == null) {
            log.warn("Card 工具创建响应缺少卡属性快照，按未识别冻结: instrumentRef={}", instrumentRef);
            return new CardInstrumentResult.CardAttributes(null, null, null, null, null, null, null, null);
        }
        if (!validVersion(body.getBinLibraryVersionNo())) {
            throw new ErrorException("Card 卡属性快照无效");
        }
        if (!body.isRecognized()) {
            if (body.getMatchedPrefixLength() != null || body.getCardBrand() != null || body.getFundingType() != null
                || body.getCardLevel() != null || body.getCardProduct() != null || body.getIssuerCountry() != null
                || body.getIssuerRegion() != null) {
                throw new ErrorException("Card 未识别卡属性快照不应含属性");
            }
            return new CardInstrumentResult.CardAttributes(null, null, null, null, null, null,
                body.getBinLibraryVersionNo(), null);
        }
        if (body.getBinLibraryVersionNo() == null || !validRecognized(body)) {
            throw new ErrorException("Card 已识别卡属性快照无效");
        }
        return new CardInstrumentResult.CardAttributes(body.getCardBrand(), body.getFundingType(), body.getCardLevel(),
            body.getCardProduct(), body.getIssuerCountry(), body.getIssuerRegion(), body.getBinLibraryVersionNo(),
            body.getMatchedPrefixLength());
    }

    /** 版本号为空（没有生效版本）或正整数。 */
    static boolean validVersion(Integer versionNo) {
        return versionNo == null || versionNo > 0;
    }

    /** 已识别快照：必填属性有值，闭合代码为 card 发布的 Code，开放代码符合格式。 */
    static boolean validRecognized(CreateCardInstrumentResponseBody.CardAttributes body) {
        Integer length = body.getMatchedPrefixLength();
        return length != null && length >= 6 && length <= 8
            && EnumUtils.getEnumByCode(CardBrand.class, body.getCardBrand()) != null
            && (body.getFundingType() == null || EnumUtils.getEnumByCode(CardFundingType.class, body.getFundingType()) != null)
            && (body.getCardProduct() == null || EnumUtils.getEnumByCode(CardProduct.class, body.getCardProduct()) != null)
            && (body.getCardLevel() == null || body.getCardLevel().matches("[A-Z0-9_]{1,32}"))
            && body.getIssuerCountry() != null && body.getIssuerCountry().matches("[A-Z]{2}")
            && body.getIssuerRegion() != null && body.getIssuerRegion().matches("[A-Z0-9_]{1,16}");
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
