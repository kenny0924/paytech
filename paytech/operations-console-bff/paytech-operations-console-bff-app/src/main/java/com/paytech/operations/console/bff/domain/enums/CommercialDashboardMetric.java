package com.paytech.operations.console.bff.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 商务工作台的指标卡片；Code 是工作台响应 metricCode 的合同值，声明顺序即卡片顺序。 */
public enum CommercialDashboardMetric implements CodeEnum {
    /** 待审批的条款版本 */
    PENDING_APPROVAL_VERSIONS("PENDING_APPROVAL_VERSIONS"),

    /** 已排期待生效的条款版本 */
    SCHEDULED_VERSIONS("SCHEDULED_VERSIONS"),

    /** 待签署的合同 */
    PENDING_SIGNATURE_AGREEMENTS("PENDING_SIGNATURE_AGREEMENTS"),

    /** 生效中的合同 */
    ACTIVE_AGREEMENTS("ACTIVE_AGREEMENTS");

    private final String code;

    CommercialDashboardMetric(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
