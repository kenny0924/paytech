package com.paytech.commercial.core.dal.dataobject;

import java.time.Instant;

import com.paytech.commercial.api.enums.PricingDimension;

import lombok.Getter;
import lombok.Setter;

/**
 * t_pricing_profile_cond：定价画像的一个离散等值条件。
 * <li>同维度多行按 OR、不同维度按 AND；随画像整体替换，受画像 Root BCR 保护。</li>
 */
@Getter
@Setter
public class PricingProfileCond {
    /** 条件行标识 */
    private Long profileConditionId;

    /** 所属画像 */
    private Long pricingProfileId;

    /** 定价维度 */
    private PricingDimension dimensionCode;

    /** 该维度的一个规范化值 */
    private String conditionValue;

    /** 记录创建的 UTC 微秒时间 */
    private Instant createTime;

    /** 记录最近一次持久化更新的 UTC 微秒时间 */
    private Instant updateTime;
}
