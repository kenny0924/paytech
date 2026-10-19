package com.paytech.commercial.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 商业条款版本费用金额是否含税（t_commercial_term_version.fee_tax_treatment）。 */
public enum FeeTaxTreatment implements CodeEnum {
    /** 费用金额已含税 */
    INCLUSIVE("INCLUSIVE"),

    /** 费用金额不含税 */
    EXCLUSIVE("EXCLUSIVE");

    private final String code;

    FeeTaxTreatment(String code) {
        this.code = code;
    }

    /** 数据库与接口使用的稳定编码。 */
    @Override
    public String getCode() {
        return code;
    }
}
