package com.paytech.commercial.core.dal.dataobject;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/**
 * t_commercial_term_component_band：阶梯组件的一个档位。
 * <li>区间 [lower_bound, upper_bound)，从 0 开始连续无重叠，末档上界为空；每档 rate 与 fixed_amount 恰好一个非空。</li>
 */
@Getter
@Setter
public class CommercialTermComponentBand {
    /** 档位标识 */
    private Long termBandId;

    /** 所属组件 */
    private Long termComponentId;

    /** 组件内从 1 连续的档位顺序 */
    private Integer bandNo;

    /** 包含下界 */
    private BigDecimal lowerBound;

    /** 不包含上界；末档为空 */
    private BigDecimal upperBound;

    /** 档位小数费率 */
    private BigDecimal rate;

    /** 档位固定金额 */
    private BigDecimal fixedAmount;

    /** 记录创建的 UTC 微秒时间 */
    private Instant createTime;

    /** 记录最近一次持久化更新的 UTC 微秒时间 */
    private Instant updateTime;
}
