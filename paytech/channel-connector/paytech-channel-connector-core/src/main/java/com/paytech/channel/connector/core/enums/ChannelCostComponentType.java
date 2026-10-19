package com.paytech.channel.connector.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 渠道成本组成的收费语义；本库只保存，金额算法由 billing 对所有类型相同处理。 */
@Getter
@AllArgsConstructor
public enum ChannelCostComponentType implements CodeEnum {
    /** 交易处理费 */
    PROCESSING_FEE("PROCESSING_FEE"),

    /** 跨境费 */
    CROSS_BORDER_FEE("CROSS_BORDER_FEE"),

    /** 退款处理费 */
    REFUND_FEE("REFUND_FEE"),

    /** 争议处理费 */
    DISPUTE_FEE("DISPUTE_FEE");

    /** 数据库存储及业务比较使用的稳定代码。 */
    private final String code;
}
