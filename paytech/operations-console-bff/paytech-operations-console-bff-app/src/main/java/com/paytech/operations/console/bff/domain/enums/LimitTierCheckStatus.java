package com.paytech.operations.console.bff.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 上线复核清单系统项的核对结果；Code 是工作台 checklist.items[].status 的合同值。 */
public enum LimitTierCheckStatus implements CodeEnum {
    /** 已满足。 */
    OK("OK"),

    /** 已读取，但未满足。 */
    NOT_OK("NOT_OK"),

    /** 来源读取失败或超时；不能提交上线复核。 */
    UNAVAILABLE("UNAVAILABLE");

    private final String code;

    LimitTierCheckStatus(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
