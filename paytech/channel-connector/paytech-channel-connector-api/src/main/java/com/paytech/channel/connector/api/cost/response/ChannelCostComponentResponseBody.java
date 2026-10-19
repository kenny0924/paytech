package com.paytech.channel.connector.api.cost.response;

import lombok.Getter;
import lombok.Setter;

/** 渠道成本组成。 */
@Getter
@Setter
public class ChannelCostComponentResponseBody {
    /** 成本组成主键（t_channel_cost_component.id），十进制字符串；命中结果与 billing 行证据 sourceRuleComponentId 引用它。 */
    private String componentId;

    /** 范围内组成代码。 */
    private String componentCode;

    /** 收费语义：PROCESSING_FEE、CROSS_BORDER_FEE、REFUND_FEE、DISPUTE_FEE。 */
    private String componentType;

    /** 金额计算基数：TRANSACTION_AMOUNT。 */
    private String calculationBase;

    /**
     * 十进制费率
     * <li>去掉末尾零的普通十进制字符串，例如 "0.029"；未设置时为空。</li>
     */
    private String rateValue;

    /**
     * 固定费
     * <li>去掉末尾零的普通十进制字符串，例如 "0.3"；未设置时为空。</li>
     */
    private String fixedAmount;

    /**
     * 固定费币种
     * <li>与固定费成对为空。</li>
     */
    private String currency;

    /**
     * 计算结果下限
     * <li>去掉末尾零的普通十进制字符串，例如 "0.3"；未设置时为空。</li>
     */
    private String minAmount;

    /**
     * 计算结果上限
     * <li>去掉末尾零的普通十进制字符串，例如 "0.3"；未设置时为空。</li>
     */
    private String maxAmount;

    /** 舍入方式：HALF_UP。 */
    private String roundingMode;
}
