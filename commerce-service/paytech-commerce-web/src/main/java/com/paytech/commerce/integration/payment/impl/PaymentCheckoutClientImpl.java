package com.paytech.commerce.integration.payment.impl;

import java.time.DateTimeException;
import java.time.Instant;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.paytech.commerce.core.configuration.CommercePaymentProperties;
import com.paytech.commerce.domain.checkout.CheckoutUpstreamProtocolException;
import com.paytech.commerce.domain.customer.CheckoutSubmissionModels.CardInput;
import com.paytech.commerce.domain.customer.CheckoutSubmissionModels.CardInstrument;
import com.paytech.commerce.domain.customer.CheckoutSubmissionModels.PaymentReference;
import com.paytech.commerce.domain.customer.CustomerCheckoutModels.IntentRequest;
import com.paytech.commerce.domain.customer.CustomerCheckoutModels.IntentResult;
import com.paytech.commerce.integration.payment.PaymentCheckoutClient;
import com.paytech.foundation.money.Money;
import com.paytech.payment.api.execution.PaymentIntentApi;
import com.paytech.payment.api.execution.request.CreateIntentRequestBody;
import com.paytech.payment.api.instrument.PaymentInstrumentApi;
import com.paytech.payment.api.instrument.request.ApplyCardInstrumentRequestBody;

import lombok.AllArgsConstructor;

/** 只适配 Payment 正式 DTO；HTTP、MDC 和错误解码交给公共客户端。 */
@Component
@AllArgsConstructor
public class PaymentCheckoutClientImpl implements PaymentCheckoutClient {
    private final PaymentIntentApi intents;
    private final PaymentInstrumentApi instruments;
    private final CommercePaymentProperties properties;

    /** 发送原订单冻结值，不要求 Payment 回显金额、OE 或内部版本。 */
    @Override
    public IntentResult createIntent(IntentRequest request) {
        var body = new CreateIntentRequestBody();
        body.setClientId(Long.toString(request.getClientId()));
        body.setSourceType(request.getSourceType());
        body.setSourceId(Long.toString(request.getOrderId()));
        body.setEntityNo(request.getEntityNo());
        body.setClientPricingVersionId(Long.toString(request.getClientPricingVersionId()));
        body.setPartnerShareVersionId(nullableId(request.getPartnerShareVersionId()));
        body.setPlatformChargeVersionId(nullableId(request.getPlatformChargeVersionId()));
        body.setIndustryCode(request.getIndustryCode());
        body.setMccCode(request.getMccCode());
        body.setConsumerCountryCode(request.getConsumerCountryCode());
        body.setAmount(Money.of(request.getCurrency(), request.getTotalAmount()).toAmountString());
        body.setCurrency(request.getCurrency());
        body.setExpiresAt(request.getExpiresAt().toString());
        var result = responseBody(intents.createIntent(body));
        if (result.getIntentId() == null || !result.getIntentId().matches("[1-9][0-9]{0,18}")
            || result.getStatus() == null || !Set.of("OPEN", "SUCCEEDED", "CANCELLED", "EXPIRED").contains(result.getStatus())) {
            throw new CheckoutUpstreamProtocolException("Payment Intent 响应无效");
        }
        try {
            return new IntentResult(Long.parseLong(result.getIntentId()), result.getStatus());
        } catch (NumberFormatException invalidId) {
            throw new CheckoutUpstreamProtocolException("Payment Intent 标识超出范围", invalidId);
        }
    }

    /** 卡原值只发送到专用工具接口，返回瞬时 Token 供后续支付使用。 */
    @Override
    public CardInstrument applyCardInstrument(long clientId, PaymentReference reference, CardInput card) {
        var identity = card.identity();
        var cardBody = new ApplyCardInstrumentRequestBody.Card();
        cardBody.setPan(identity.pan());
        cardBody.setExpiryMonth(identity.expiryMonth());
        cardBody.setExpiryYear(identity.expiryYear());
        cardBody.setCardholderName(identity.cardholderName());
        cardBody.setCvv(card.cvv());
        var body = new ApplyCardInstrumentRequestBody();
        body.setClientId(Long.toString(clientId));
        body.setIntentId(Long.toString(reference.intentId()));
        body.setSourceSubmissionId(Long.toString(reference.submissionId()));
        body.setPaymentMethodCode(properties.getCardPaymentMethodCode());
        body.setCard(cardBody);
        var result = responseBody(instruments.applyCardInstrument(body));
        if (result.getPaymentInstrumentToken() == null || !result.getPaymentInstrumentToken().matches("[A-Za-z0-9_-]{43}")
            || result.getExpiresAt() == null
            || !result.getExpiresAt().matches("[1-9][0-9]{3}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]{1,6})?Z")) {
            throw new CheckoutUpstreamProtocolException("Payment 卡工具响应无效");
        }
        try {
            return new CardInstrument(body.getPaymentMethodCode(), result.getPaymentInstrumentToken(), Instant.parse(result.getExpiresAt()));
        } catch (DateTimeException invalidTime) {
            throw new CheckoutUpstreamProtocolException("Payment 卡工具期限无效", invalidTime);
        }
    }

    /** 两个接口均约定 HTTP 200 和非空 DTO，其他成功状态也不能视作有效创建。 */
    private <T> T responseBody(ResponseEntity<T> response) {
        if (response.getStatusCode().value() != 200 || response.getBody() == null) {
            throw new CheckoutUpstreamProtocolException("Payment 创建响应状态或内容无效");
        }
        return response.getBody();
    }

    /** 可选定价版本缺失时保持 null，不传空串或字符串 null。 */
    private String nullableId(Long id) {
        return id == null ? null : id.toString();
    }
}
