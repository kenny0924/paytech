package com.paytech.commerce.domain.enums;
import com.paytech.foundation.common.enums.CodeEnum;
/** Commerce 退款状态；未知支付结果始终保留 PROCESSING。 */
public enum RefundStatus implements CodeEnum {
    PENDING,

    PROCESSING,

    SUCCEEDED,

    FAILED,

    CANCELLED;

    @Override
    public String getCode() { return name(); }

    /** 只有权威结果可以结束退款。 */
    public boolean terminal() { return this == SUCCEEDED || this == FAILED || this == CANCELLED; }
}
