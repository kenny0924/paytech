package com.paytech.payment.api.execution.response;

import lombok.Getter;
import lombok.Setter;

/** 支付提交结果 */
@Getter
@Setter
public class PayResponseBody {
    /**
     * 支付尝试 ID
     * <li>本次新建或幂等命中的尝试，使用正十进制 long 字符串。</li>
     */
    private String attemptId;

    /**
     * 支付尝试状态
     * <li>PROCESSING、SUCCEEDED、FAILED 或 CANCELLED。</li>
     * <li>PROCESSING 不表示已扣款，手动授权后未收款仍使用该状态。</li>
     */
    private String status;
}
