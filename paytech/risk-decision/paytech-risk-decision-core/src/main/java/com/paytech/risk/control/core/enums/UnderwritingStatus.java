package com.paytech.risk.control.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Client 承保版本状态。 */
public enum UnderwritingStatus implements CodeEnum {
    /** 草稿，可编辑。 */
    DRAFT("DRAFT"),

    /** HIGH 风险已提交，等待复核。 */
    SUBMITTED("SUBMITTED"),

    /** 已批准，内容不可变。 */
    APPROVED("APPROVED"),

    /** 已拒绝。 */
    DECLINED("DECLINED"),

    /** 曾为有效结论，已被新版本替代。 */
    SUPERSEDED("SUPERSEDED");

    private final String code;

    UnderwritingStatus(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
