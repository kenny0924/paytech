package com.paytech.commerce.test.builder.customer;

import java.time.Instant;

import com.paytech.payment.api.execution.response.CreateIntentResponseBody;
import com.paytech.payment.api.instrument.response.ApplyCardInstrumentResponseBody;

import lombok.Builder;

/** 按名配置提供方 DTO，默认值仅用于测试中的合成响应。 */
@Builder
public class PaymentResponseBuilder {
    @Builder.Default
    private String intentId = "601";
    @Builder.Default
    private String status = "OPEN";
    @Builder.Default
    private String token = "t".repeat(43);
    private Instant expiresAt;

    /** 构造正式创建响应，不扩展提供方合同。 */
    public CreateIntentResponseBody intent() {
        var body = new CreateIntentResponseBody();
        body.setIntentId(intentId);
        body.setStatus(status);
        return body;
    }

    /** 构造正式工具响应，允许负例传入非法字段。 */
    public ApplyCardInstrumentResponseBody instrument() {
        var body = new ApplyCardInstrumentResponseBody();
        body.setPaymentInstrumentToken(token);
        body.setExpiresAt(expiresAt == null ? null : expiresAt.toString());
        return body;
    }
}
