package com.paytech.billing.domain.view;

import java.math.BigDecimal;
import java.util.List;

import com.paytech.billing.core.enums.AmountRoundingMode;
import com.paytech.billing.core.enums.ChannelCostCalculationBase;
import com.paytech.billing.core.enums.ChannelCostComponentType;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 预计渠道成本估算结果与规则证据
 * <li>每个组成对应一行 component_type=CHANNEL_COST 的交易经济组件，证据字段与其 source_* 列一一对应。</li>
 */
@Getter
@AllArgsConstructor
public class ChannelCostEstimate {
    /** 成本方案主键。 */
    private final long planId;

    /** 命中版本主键，对应 source_rule_version_id。 */
    private final long costVerId;

    /** 命中版本号。 */
    private final int costVer;

    /** 命中版本内容摘要，对应 source_rule_content_hash。 */
    private final String contentHash;

    /** 命中适用范围主键，对应 source_rule_scope_id。 */
    private final long scopeId;

    /** 命中适用范围序号。 */
    private final int scopeNo;

    /** 命中范围的 Channel 协议引用。 */
    private final String channelAgreementRef;

    /** 动作类别，对应 source_trigger_code。 */
    private final String actionType;

    /** 成本币种。 */
    private final String currency;

    /** 按组成代码升序的逐项结果。 */
    private final List<Component> components;

    /** 全部组成舍入后金额合计。 */
    private final BigDecimal totalPostedAmount;

    /** 一个成本组成的计算结果。 */
    @Getter
    @AllArgsConstructor
    public static class Component {
        /** 组成主键，对应 source_rule_component_id。 */
        private final long componentId;

        /** 组成代码，对应 source_component_code。 */
        private final String componentCode;

        /** 收费语义。 */
        private final ChannelCostComponentType componentType;

        /** 计算基数，对应 source_calculation_base。 */
        private final ChannelCostCalculationBase calculationBase;

        /** 费率，可为空。 */
        private final BigDecimal rateValue;

        /** 固定费，可为空。 */
        private final BigDecimal fixedAmount;

        /** 固定费币种，可为空。 */
        private final String fixedCurrency;

        /** 下限，可为空。 */
        private final BigDecimal minAmount;

        /** 上限，可为空。 */
        private final BigDecimal maxAmount;

        /** 舍入方式。 */
        private final AmountRoundingMode roundingMode;

        /** 实际使用的计费基数。 */
        private final BigDecimal basisAmount;

        /** 受上下限约束后、按 8 位小数的原始金额。 */
        private final BigDecimal rawAmount;

        /** 按币种精度舍入后的金额。 */
        private final BigDecimal postedAmount;
    }
}
