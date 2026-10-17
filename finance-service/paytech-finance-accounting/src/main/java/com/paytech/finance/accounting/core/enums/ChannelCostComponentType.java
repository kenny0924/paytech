package com.paytech.finance.accounting.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 渠道成本组成的收费语义；估算算法对所有类型相同。 */
public enum ChannelCostComponentType implements CodeEnum {
    /** 交易处理费 */
    PROCESSING_FEE,

    /** 跨境费 */
    CROSS_BORDER_FEE,

    /** 退款处理费 */
    REFUND_FEE,

    /** 争议处理费 */
    DISPUTE_FEE;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
