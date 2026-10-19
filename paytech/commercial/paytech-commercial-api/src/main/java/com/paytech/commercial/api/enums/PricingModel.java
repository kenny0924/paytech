package com.paytech.commercial.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 商业条款版本的定价模型（t_commercial_term_version.pricing_model）；本期应用只接受 BLENDED。 */
public enum PricingModel implements CodeEnum {
    /** 综合费率 */
    BLENDED("BLENDED"),

    /** 交换费加成（Interchange++） */
    INTERCHANGE_PLUS_PLUS("INTERCHANGE_PLUS_PLUS");

    private final String code;

    PricingModel(String code) {
        this.code = code;
    }

    /** 数据库与接口使用的稳定编码。 */
    @Override
    public String getCode() {
        return code;
    }
}
