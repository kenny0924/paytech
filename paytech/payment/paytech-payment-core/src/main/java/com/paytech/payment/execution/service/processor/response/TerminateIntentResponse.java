package com.paytech.payment.execution.service.processor.response;

import com.paytech.payment.execution.core.enums.IntentStatus;
import com.paytech.payment.execution.core.enums.TerminationDecision;

import lombok.Getter;
import lombok.Setter;

/** 终止支付意图结果 */
@Getter
@Setter
public class TerminateIntentResponse {
    /** 支付意图 ID */
    private Long intentId;

    /** 支付意图当前状态 */
    private IntentStatus status;

    /** 终止决定 */
    private TerminationDecision decision;
}
