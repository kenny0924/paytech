package com.paytech.commercial.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 组件计算基数（t_commercial_term_component.calculation_base）。 */
public enum CalculationBase implements CodeEnum {
    /** 交易总额 */
    GROSS_AMOUNT("GROSS_AMOUNT"),

    /** 交易净额 */
    NET_AMOUNT("NET_AMOUNT"),

    /** Client 实收费用；PARTNER_SHARE 组件使用 */
    ACTUAL_CLIENT_FEE("ACTUAL_CLIENT_FEE"),

    /** 每次计费事件固定收取 */
    FIXED_PER_EVENT("FIXED_PER_EVENT"),

    /** Client 实收费用减同场景 PARTNER_BUY_RATE 命中金额，负值取 0；仅 PARTNER_SHARE 组件可用 */
    BUY_RATE_DIFFERENCE("BUY_RATE_DIFFERENCE");

    private final String code;

    CalculationBase(String code) {
        this.code = code;
    }

    /** 数据库与接口使用的稳定编码。 */
    @Override
    public String getCode() {
        return code;
    }
}
