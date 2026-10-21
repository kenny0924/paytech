package com.paytech.commercial.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 商业条款方案的 Owner 类型（t_commercial_term_plan.owner_type）。 */
public enum CommercialTermOwnerType implements CodeEnum {
    /** Client 条款方案，属于 CLIENT 合同 */
    CLIENT("CLIENT"),

    /** Local Partner 条款方案，版本冻结 PARTNER 合同 */
    LOCAL_PARTNER("LOCAL_PARTNER"),

    /** 平台标准价目模板：按 OE 维护，不属于合同，不参与运行时取价 */
    PLATFORM_TEMPLATE("PLATFORM_TEMPLATE");

    private final String code;

    CommercialTermOwnerType(String code) {
        this.code = code;
    }

    /** 数据库与接口使用的稳定编码。 */
    @Override
    public String getCode() {
        return code;
    }
}
