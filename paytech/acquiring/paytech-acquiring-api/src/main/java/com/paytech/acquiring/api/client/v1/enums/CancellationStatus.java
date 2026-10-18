package com.paytech.acquiring.api.client.v1.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Lifecycle state returned while an order cancellation awaits a definitive result. */
public enum CancellationStatus implements CodeEnum {
    /** Cancellation remains open and the Client should poll after the response retry delay. */
    PENDING("PENDING");

    private final String code;

    CancellationStatus(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
