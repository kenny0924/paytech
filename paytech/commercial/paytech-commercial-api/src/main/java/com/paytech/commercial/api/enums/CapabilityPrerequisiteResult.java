package com.paytech.commercial.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 单项前置检查结论。 */
public enum CapabilityPrerequisiteResult implements CodeEnum {
    /** 满足 */
    SATISFIED("SATISFIED"),

    /** 明确不满足 */
    UNSATISFIED("UNSATISFIED"),

    /** 查询失败或超时，本次不迁移状态 */
    UNKNOWN("UNKNOWN"),

    /** 未接入或不适用，按满足处理 */
    SKIPPED("SKIPPED");

    private final String code;

    CapabilityPrerequisiteResult(String code) {
        this.code = code;
    }

    /** 接口使用的稳定编码。 */
    @Override
    public String getCode() {
        return code;
    }
}
