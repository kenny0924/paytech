package com.paytech.payment.execution.service.processor.response;

import java.math.BigDecimal;
import java.time.Instant;

import com.paytech.payment.execution.core.enums.AttemptStatus;
import com.paytech.payment.execution.core.enums.InteractionType;

import lombok.Getter;
import lombok.Setter;

/** 按来源提交查询的原支付结果 */
@Getter
@Setter
public class GetSubmissionResponse {
    /** 来源提交 ID */
    private Long sourceSubmissionId;

    /** 支付意图 ID */
    private Long intentId;

    /** 支付尝试 ID */
    private Long attemptId;

    /** 精确支付方式代码 */
    private String paymentMethodCode;

    /** 支付尝试状态 */
    private AttemptStatus status;

    /** 已捕获金额 */
    private BigDecimal capturedAmount;

    /** 币种 */
    private String currency;

    /**
     * 待消费者完成的交互类型
     * <li>仅处理中且交互未过期时有值；不含跳转地址。</li>
     */
    private InteractionType interactionType;

    /** 交互截止时间；与交互类型同时出现。 */
    private Instant interactionExpiresAt;

    /**
     * 失败代码
     * <li>仅失败时取最后一个动作的渠道响应码。</li>
     */
    private String failureCode;

    /** 本次读取时间 */
    private Instant observedAt;
}
