package com.paytech.finance.accounting.core.dal.dataobject;

import com.paytech.finance.accounting.core.enums.AmountRoundingMode;
import com.paytech.finance.accounting.core.enums.ChannelCostCalculationBase;
import com.paytech.finance.accounting.core.enums.ChannelCostComponentType;
import java.math.BigDecimal;
import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** t_channel_cost_component：适用范围下的费率、固定费与上下限；不可变。 */
@Getter
@Setter
public class ChannelCostComponent {
    /**
     * 渠道成本组成的 Snowflake 主键
     */
    private Long id;

    /**
     * 所属适用范围主键
     */
    private Long scopeId;

    /**
     * 同一范围内唯一的组成代码
     */
    private String componentCode;

    /**
     * 收费语义
     */
    private ChannelCostComponentType componentType;

    /**
     * 金额计算基数
     */
    private ChannelCostCalculationBase calculationBase;

    /**
     * [0,1] 十进制费率
     * <li>可为空；与固定费至少一项非空。</li>
     */
    private BigDecimal rateValue;

    /**
     * 非负固定收费金额
     * <li>可为空。</li>
     */
    private BigDecimal fixedAmount;

    /**
     * 固定费币种，与固定费成对为空
     * <li>可为空。</li>
     */
    private String currency;

    /**
     * 计算结果下限
     * <li>可为空。</li>
     */
    private BigDecimal minAmount;

    /**
     * 计算结果上限
     * <li>可为空。</li>
     */
    private BigDecimal maxAmount;

    /**
     * 归整到币种精度的方式
     */
    private AmountRoundingMode roundingMode;

    /**
     * 该记录在 Finance 数据库中的 UTC 微秒创建时间
     */
    private Instant createTime;
}
