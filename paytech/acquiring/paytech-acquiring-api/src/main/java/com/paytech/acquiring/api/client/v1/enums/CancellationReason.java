package com.paytech.commerce.client.api.v1.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Reason why an order cancellation has not yet reached a definitive result. */
public enum CancellationReason implements CodeEnum {
    /** An already submitted or unknown payment action must resolve before cancellation can finish. */
    PAYMENT_IN_PROGRESS("PAYMENT_IN_PROGRESS");

    private final String code;

    CancellationReason(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
