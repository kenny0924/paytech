package com.paytech.operations.console.bff.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 工作台指标的数据状态；Code 是工作台响应 status 的合同值。 */
public enum CommercialDashboardMetricStatus implements CodeEnum {
    /** 已由上游列表计数 */
    AVAILABLE("AVAILABLE"),

    /** 上游列表接口尚未接入，count 为 null */
    PENDING_INTEGRATION("PENDING_INTEGRATION");

    private final String code;

    CommercialDashboardMetricStatus(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
