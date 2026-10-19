package com.paytech.commercial.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 商业条款方案状态（t_commercial_term_plan.status）。 */
public enum CommercialTermPlanStatus implements CodeEnum {
    /** 草稿 */
    DRAFT("DRAFT"),

    /** 生效中，可被解析 */
    ACTIVE("ACTIVE"),

    /** 已停用，历史版本继续可读 */
    RETIRED("RETIRED");

    private final String code;

    CommercialTermPlanStatus(String code) {
        this.code = code;
    }

    /** 数据库与接口使用的稳定编码。 */
    @Override
    public String getCode() {
        return code;
    }
}
