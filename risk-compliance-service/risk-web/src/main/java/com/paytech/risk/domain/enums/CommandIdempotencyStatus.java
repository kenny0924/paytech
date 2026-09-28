package com.paytech.risk.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

public enum CommandIdempotencyStatus implements CodeEnum {

    PROCESSING,

    SUCCEEDED,

    FAILED;

    @Override
    public String getCode() {
        return name();
    }
}
