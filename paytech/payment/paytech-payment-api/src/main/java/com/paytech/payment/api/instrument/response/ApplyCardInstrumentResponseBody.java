package com.paytech.payment.api.instrument.response;

import lombok.Getter;
import lombok.Setter;

/** 申请卡支付工具响应 */
@Getter
@Setter
public class ApplyCardInstrumentResponseBody {
    /**
     * 支付工具 Token
     * <li>限定本次 Client、Intent 和来源提交，供后续 pay 使用。</li>
     */
    private String paymentInstrumentToken;

    /**
     * 工具到期时间
     * <li>UTC RFC 3339 字符串；重复申请返回原期限。</li>
     */
    private String expiresAt;

    @Override
    public String toString() {
        return "ApplyCardInstrumentResponseBody[REDACTED]";
    }
}
