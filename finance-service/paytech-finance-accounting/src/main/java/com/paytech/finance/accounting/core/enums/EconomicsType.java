package com.paytech.finance.accounting.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 交易经济结果类型。 */
public enum EconomicsType implements CodeEnum {
    /** 支付 */
    PAYMENT,

    /** 退款 */
    REFUND,

    /** 重复扣款 */
    DUPLICATE_CAPTURE,

    /** 重复扣款补偿 */
    DUPLICATE_COMPENSATION,

    /** 争议 */
    DISPUTE,

    /** 渠道成本调整 */
    COST_ADJUSTMENT;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
