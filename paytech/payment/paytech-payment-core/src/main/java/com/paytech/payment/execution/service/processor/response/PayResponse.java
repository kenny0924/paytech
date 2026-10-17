package com.paytech.payment.execution.service.processor.response;

import com.paytech.payment.execution.core.enums.AttemptStatus;
import com.paytech.payment.execution.domain.PayNextAction;

import lombok.Getter;
import lombok.Setter;

/** 支付提交结果 */
@Getter
@Setter
public class PayResponse {
    /** 支付尝试 ID */
    private Long attemptId;

    /**
     * 支付尝试状态
     */
    private AttemptStatus status;

    /**
     * 消费者交互指令
     * <li>仅本次新提交需要交互（如 3DS）时返回；不持久化，原提交重放不返回。</li>
     */
    private PayNextAction nextAction;
}
