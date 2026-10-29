package com.paytech.risk.control.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 快照时的尽调档次。 */
public enum UnderwritingDueDiligenceTier implements CodeEnum {
    /** 简化尽调。 */
    SIMPLIFIED("SIMPLIFIED"),

    /** 标准尽调。 */
    STANDARD("STANDARD"),

    /** 强化尽调。 */
    ENHANCED("ENHANCED");

    private final String code;

    UnderwritingDueDiligenceTier(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
