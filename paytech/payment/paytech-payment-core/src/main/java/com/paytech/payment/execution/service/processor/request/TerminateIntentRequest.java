package com.paytech.payment.execution.service.processor.request;

import com.paytech.payment.execution.core.enums.TerminationReason;

import lombok.Getter;
import lombok.Setter;

/** 终止支付意图请求，由 HTTP 入口完成字段校验 */
@Getter
@Setter
public class TerminateIntentRequest {
    /** 支付意图 ID */
    private Long intentId;

    /** Client ID */
    private Long clientId;

    /**
     * 终止原因
     * <li>决定安全终止后的 Intent 终态；不影响终止决定本身。</li>
     */
    private TerminationReason reason;
}
