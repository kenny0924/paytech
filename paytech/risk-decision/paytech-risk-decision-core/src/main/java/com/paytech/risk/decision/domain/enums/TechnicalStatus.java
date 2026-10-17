package com.paytech.risk.decision.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 评估或 Feature 的技术状态，与业务决定分列。 */
public enum TechnicalStatus implements CodeEnum {
    /** 执行完成并形成结果。 */
    SUCCEEDED("SUCCEEDED"),

    /** 执行失败，没有业务决定。 */
    FAILED("FAILED"),

    /** 超过超时上限，没有业务决定。 */
    TIMED_OUT("TIMED_OUT");

    private final String code;

    TechnicalStatus(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
