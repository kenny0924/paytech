package com.paytech.billing.domain;

import java.math.BigDecimal;
import java.util.List;

import lombok.Builder;
import lombok.Getter;

/**
 * 命中价格的一个计算组件；代码类字段保留 commercial 原值，由计算器按本期支持范围解释
 * <li>金额与费率已解析为精确十进制；未设置为空。</li>
 */
@Getter
@Builder
public class CommercialPriceComponent {
    /** 组件 ID，明细行 source_rule_component_id */
    private final long componentId;

    /** 组件序号，从 1 连续 */
    private final int componentNo;

    /** 组件代码 */
    private final String componentCode;

    /** 组件类型代码：PERCENTAGE、FIXED、TIERED */
    private final String componentType;

    /** 触发计费事件代码；PAY 成功只取 SALE */
    private final String triggerCode;

    /** 计算基数代码（commercial CalculationBase） */
    private final String calculationBase;

    /** 费率 */
    private final BigDecimal rate;

    /** 固定金额 */
    private final BigDecimal fixedAmount;

    /** 固定金额与上下限的币种 */
    private final String currency;

    /** 结果下限 */
    private final BigDecimal minAmount;

    /** 结果上限 */
    private final BigDecimal maxAmount;

    /** 阶梯基数代码（commercial BandBasis），仅 TIERED */
    private final String bandBasis;

    /** 阶梯模式代码：WHOLE_BASE、PROGRESSIVE，仅 TIERED */
    private final String bandMode;

    /** 档位，按 bandNo 升序；非 TIERED 为空列表 */
    private final List<CommercialPriceBand> bands;
}
