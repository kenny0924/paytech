package com.paytech.payment.api.execution.response;

import lombok.Getter;
import lombok.Setter;

/** 按来源提交查询的原支付结果（EX-I06） */
@Getter
@Setter
public class GetSubmissionResponseBody {
    /** 来源提交 ID */
    private String sourceSubmissionId;

    /** 支付意图 ID */
    private String intentId;

    /** 支付尝试 ID */
    private String attemptId;

    /** 精确支付方式代码 */
    private String paymentMethodCode;

    /**
     * 支付尝试状态
     * <li>PROCESSING、SUCCEEDED、FAILED 或 CANCELLED。</li>
     */
    private String status;

    /**
     * 已捕获金额，主单位十进制字符串
     * <li>尚未捕获时为 0。</li>
     */
    private String capturedAmount;

    /** 币种 */
    private String currency;

    /**
     * 待消费者完成的交互
     * <li>仅处理中且交互未过期时返回；不含跳转地址，跳转地址只在 pay 响应中返回一次。</li>
     */
    private Interaction interaction;

    /**
     * 失败代码
     * <li>仅失败时返回，取最后一个支付动作的渠道响应码。</li>
     */
    private String failureCode;

    /** 本次读取时间，UTC RFC 3339 */
    private String observedAt;

    /** 消费者交互 */
    @Getter
    @Setter
    public static class Interaction {
        /**
         * 交互类型
         * <li>当前为 THREE_DS_CHALLENGE。</li>
         */
        private String type;

        /** 交互截止时间，UTC RFC 3339 */
        private String expiresAt;
    }
}
