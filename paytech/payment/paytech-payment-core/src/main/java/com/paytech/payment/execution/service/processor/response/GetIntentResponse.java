package com.paytech.payment.execution.service.processor.response;

import com.paytech.payment.execution.core.dal.dataobject.PaymentIntent;
import com.paytech.payment.execution.core.enums.IntentStatus;

import lombok.Getter;
import lombok.Setter;

/** 支付意图查询结果 */
@Getter
@Setter
public class GetIntentResponse {
    /** 支付意图 ID */
    private Long intentId;

    /** 来源业务类型 */
    private String sourceType;

    /** 来源业务 ID */
    private Long sourceId;

    /** 支付意图当前状态 */
    private IntentStatus status;

    /**
     * 当前活动尝试 ID
     * <li>没有活动尝试时为空。</li>
     */
    private Long activeAttemptId;

    /**
     * 成功收款尝试 ID
     * <li>尚未成功收款时为空。</li>
     */
    private Long successfulAttemptId;

    /** 从同一条 Intent 记录组装查询结果；按 ID 与按来源查询共用。 */
    public static GetIntentResponse of(PaymentIntent intent) {
        GetIntentResponse response = new GetIntentResponse();
        response.setIntentId(intent.getPaymentIntentId());
        response.setSourceType(intent.getSourceType());
        response.setSourceId(intent.getSourceId());
        response.setStatus(intent.getIntentStatus());
        response.setActiveAttemptId(intent.getActiveAttemptId());
        response.setSuccessfulAttemptId(intent.getSuccessfulAttemptId());
        return response;
    }
}
