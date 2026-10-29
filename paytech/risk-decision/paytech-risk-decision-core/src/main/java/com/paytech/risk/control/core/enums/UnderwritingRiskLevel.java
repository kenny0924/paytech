package com.paytech.risk.control.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 承保使用的风险级别（尽调 CRR 与复核级别共用）。 */
public enum UnderwritingRiskLevel implements CodeEnum {
    /** 低风险。 */
    LOW("LOW"),

    /** 中风险；CRR 缺失时的复核级别。 */
    MEDIUM("MEDIUM"),

    /** 高风险，须四眼复核。 */
    HIGH("HIGH");

    private final String code;

    UnderwritingRiskLevel(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
