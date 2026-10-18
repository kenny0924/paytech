package com.paytech.payment.api.execution.response;

import lombok.Getter;
import lombok.Setter;

/** 支付意图查询响应 */
@Getter
@Setter
public class GetIntentResponseBody {
    /** 支付意图 ID */
    private String intentId;

    /**
     * 来源业务类型
     * <li>当前为 COMMERCE_ORDER。</li>
     */
    private String sourceType;

    /**
     * 来源业务 ID
     * <li>COMMERCE_ORDER 对应 Commerce 内部订单 ID。</li>
     */
    private String sourceId;

    /**
     * 支付意图当前状态
     * <li>OPEN、SUCCEEDED、CANCELLED 或 EXPIRED。</li>
     * <li>OPEN 也可能处于支付处理中或授权后等待捕获。</li>
     */
    private String status;

    /**
     * 当前活动尝试 ID
     * <li>没有活动尝试时为空。</li>
     */
    private String activeAttemptId;

    /**
     * 成功收款尝试 ID
     * <li>尚未成功收款时为空。</li>
     */
    private String successfulAttemptId;
}
