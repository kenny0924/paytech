package com.paytech.payment.execution.service.processor.response;

import java.time.Instant;

import com.paytech.payment.execution.core.enums.AttemptStatus;
import com.paytech.payment.execution.core.enums.PayResultConfirmation;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 确认支付结果的结论 */
@Getter
@AllArgsConstructor
public class ConfirmPayResultResponse {
    /** 确认结论 */
    private final PayResultConfirmation confirmation;

    /** 确认后的支付尝试状态 */
    private final AttemptStatus attemptStatus;

    /**
     * 当前动作的创建时间
     * <li>查单恢复任务据此判断是否已超过 24 小时转人工；尝试尚无动作时为 null。</li>
     */
    private final Instant actionCreateTime;
}
