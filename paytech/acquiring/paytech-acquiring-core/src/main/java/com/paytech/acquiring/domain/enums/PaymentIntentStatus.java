package com.paytech.commerce.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Commerce 本地 Payment Intent 投影的闭合状态。 */
public enum PaymentIntentStatus implements CodeEnum {
    REQUIRES_PAYMENT("REQUIRES_PAYMENT"),

    PROCESSING("PROCESSING"),

    REQUIRES_ACTION("REQUIRES_ACTION"),

    SUCCEEDED("SUCCEEDED"),

    FAILED("FAILED"),

    EXPIRED("EXPIRED"),

    CANCELLED("CANCELLED");

    private final String code;

    PaymentIntentStatus(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }

    /** 从跨边界稳定 code 解析内部枚举，不依赖 Java 常量名。 */
    public static PaymentIntentStatus fromCode(String code) {
        for (PaymentIntentStatus value : values()) {
            if (value.getCode().equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Payment Intent Status code 不受支持");
    }
}
