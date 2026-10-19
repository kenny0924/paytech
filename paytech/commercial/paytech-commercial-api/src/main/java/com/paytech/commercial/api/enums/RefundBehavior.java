package com.paytech.commercial.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 原交易退款时组件费用的处理方式（t_commercial_term_component.refund_behavior）；非支付类事件的组件为 KEEP。 */
public enum RefundBehavior implements CodeEnum {
    /** 全额退还该组件费用 */
    RETURN_ALL("RETURN_ALL"),

    /** 按退款比例退还 */
    RETURN_PRORATA("RETURN_PRORATA"),

    /** 不退还 */
    KEEP("KEEP");

    private final String code;

    RefundBehavior(String code) {
        this.code = code;
    }

    /** 数据库与接口使用的稳定编码。 */
    @Override
    public String getCode() {
        return code;
    }
}
