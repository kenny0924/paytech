package com.paytech.payment.api.execution.response;

import lombok.Getter;
import lombok.Setter;

/** 终止支付意图响应 */
@Getter
@Setter
public class TerminateIntentResponseBody {
    /** 支付意图 ID */
    private String intentId;

    /**
     * 支付意图当前状态
     * <li>OPEN、SUCCEEDED、CANCELLED 或 EXPIRED。</li>
     */
    private String status;

    /**
     * 终止决定
     * <li>TERMINATED：本次终止；ALREADY_TERMINATED：此前已取消或过期。</li>
     * <li>PENDING：存在未决尝试；REVERSAL_REQUIRED：已有未释放授权。</li>
     * <li>ALREADY_PAID：已收款，资金退回须走退款。</li>
     */
    private String decision;
}
