package com.paytech.payment.execution.service.processor.response;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** 卡工具申请结果 */
@Getter
@Setter
public class ApplyCardInstrumentResponse {
    /** 支付工具 Token */
    private String paymentInstrumentToken;

    /** 工具到期时间 */
    private Instant expiresAt;

    @Override
    public String toString() {
        return "ApplyCardInstrumentResponse[REDACTED]";
    }
}
