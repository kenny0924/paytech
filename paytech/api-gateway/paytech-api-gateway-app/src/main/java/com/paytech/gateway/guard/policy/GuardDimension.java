package com.paytech.gateway.guard.policy;

import com.paytech.foundation.common.enums.CodeEnum;

/** Redis Guard 的独立计数维度。 */
public enum GuardDimension implements CodeEnum {
    // 声明顺序刻意不同于策略顺序；策略排序只能使用 sortOrder。
    AUTHENTICATED_SUBJECT("authenticated-subject", 3),

    SHARED_ROUTE("shared-route", 1),

    CLIENT_IP("client-ip", 2),

    TENANT("tenant", 4);

    private final String code;
    private final int sortOrder;

    GuardDimension(String code, int sortOrder) {
        this.code = code;
        this.sortOrder = sortOrder;
    }

    @Override
    public String getCode() {
        return code;
    }

    public int sortOrder() {
        return sortOrder;
    }
}
