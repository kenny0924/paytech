package com.paytech.payment.execution.service.processor.response;

import com.paytech.payment.execution.core.enums.IntentStatus;

import lombok.Getter;
import lombok.Setter;

/** 创建支付意图结果 */
@Getter
@Setter
public class CreateIntentResponse {
    /** 支付意图 ID */
    private Long intentId;

    /** 支付意图当前状态 */
    private IntentStatus status;
}
