package com.paytech.payment.execution.service.processor.response;

import com.paytech.payment.execution.core.enums.AttemptStatus;

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
}
