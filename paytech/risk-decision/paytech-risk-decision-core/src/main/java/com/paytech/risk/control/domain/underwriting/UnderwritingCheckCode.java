package com.paytech.risk.control.domain.underwriting;

import com.paytech.foundation.common.enums.CodeEnum;

/** 自动承保判定的检查项，声明顺序即响应顺序。 */
public enum UnderwritingCheckCode implements CodeEnum {
    /** 存在 ACTIVE 规则版本。 */
    RULE_CONFIGURED("RULE_CONFIGURED"),

    /** 尽调 CRR 为 LOW（为空不通过）。 */
    CRR_LOW("CRR_LOW"),

    /** 已申报预计月流水与币种。 */
    VOLUME_PROVIDED("VOLUME_PROVIDED"),

    /** 规则版本有该币种阈值。 */
    CURRENCY_CONFIGURED("CURRENCY_CONFIGURED"),

    /** 预计月流水不超过阈值。 */
    VOLUME_WITHIN_THRESHOLD("VOLUME_WITHIN_THRESHOLD"),

    /** MCC 已确认且不是卡组织高风险。 */
    MCC_NOT_NETWORK_HIGH_RISK("MCC_NOT_NETWORK_HIGH_RISK"),

    /** MCC 已确认且不在规则排除清单。 */
    MCC_NOT_EXCLUDED("MCC_NOT_EXCLUDED"),

    /** 运营已确认 MCC / 行业分类。 */
    CLASSIFICATION_CONFIRMED("CLASSIFICATION_CONFIRMED");

    private final String code;

    UnderwritingCheckCode(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
