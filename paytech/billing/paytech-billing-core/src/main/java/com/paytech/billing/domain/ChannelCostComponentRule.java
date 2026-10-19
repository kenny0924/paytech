package com.paytech.billing.domain;

import java.math.BigDecimal;

import com.paytech.billing.core.enums.AmountRoundingMode;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 一个渠道成本组件的计算参数（来自 channel-connector 命中接口的 components[]）
 * <li>componentType 与 calculationBase 是 channel-connector 的目录代码，计算器不解释，只随结果带回作为证据。</li>
 */
@Getter
@AllArgsConstructor
public class ChannelCostComponentRule {
    /** channel-connector 组件稳定 ID，对应明细行 source_rule_component_id。 */
    private final Long componentId;

    /** 同一范围内唯一的组件代码。 */
    private final String componentCode;

    /** 收费语义代码（如 PROCESSING_FEE）。 */
    private final String componentType;

    /** 计算基数语义代码（如 TRANSACTION_AMOUNT）。 */
    private final String calculationBase;

    /**
     * 十进制费率
     * <li>可为空；与固定费至少一项非空。</li>
     */
    private final BigDecimal rateValue;

    /**
     * 固定费
     * <li>可为空。</li>
     */
    private final BigDecimal fixedAmount;

    /**
     * 计算结果下限
     * <li>可为空。</li>
     */
    private final BigDecimal minAmount;

    /**
     * 计算结果上限
     * <li>可为空。</li>
     */
    private final BigDecimal maxAmount;

    /** 归整到币种精度的方式。 */
    private final AmountRoundingMode roundingMode;
}
