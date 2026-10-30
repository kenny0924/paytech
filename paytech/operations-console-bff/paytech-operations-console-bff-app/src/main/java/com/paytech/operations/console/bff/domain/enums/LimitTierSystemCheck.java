package com.paytech.operations.console.bff.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 上线复核清单的四个系统项（B5），各带来源系统；Code 是工作台 checklist.items[].code 的合同值。 */
public enum LimitTierSystemCheck implements CodeEnum {
    /** Client 角色资格已批准且当前有效（compliance）。 */
    QUALIFICATION("QUALIFICATION", "compliance"),

    /** 有有效的 APPROVED 承保结论（risk-decision）。 */
    UNDERWRITING("UNDERWRITING", "risk"),

    /** 有 ACTIVE 的 CLIENT 合同（commercial）。 */
    AGREEMENT("AGREEMENT", "commercial"),

    /** 该合同至少一个支付方式开通项执行状态为 ENABLED（commercial）。 */
    CAPABILITIES("CAPABILITIES", "commercial");

    private final String code;

    private final String source;

    LimitTierSystemCheck(String code, String source) {
        this.code = code;
        this.source = source;
    }

    @Override
    public String getCode() {
        return code;
    }

    /** 来源系统。 */
    public String source() {
        return source;
    }
}
