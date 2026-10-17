package com.paytech.commerce.domain.enums;
import com.paytech.foundation.common.enums.CodeEnum;
/** 退款创建方；Operations 退款不具有 Client Refund ID。 */
public enum RefundRequestOrigin implements CodeEnum {
    CLIENT,

    OPERATIONS;

    @Override
    public String getCode() { return name(); }
}
