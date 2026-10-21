package com.paytech.commercial.core.dal.dataobject;

import java.time.Instant;

import com.paytech.commercial.api.enums.PricingDimension;

import lombok.Getter;
import lombok.Setter;

/**
 * t_product_capability_dimension：可开通项的可配维度。
 * <li>uk(capability_code, dimension_code)；随可开通项整体替换，审计归 PRODUCT_CATALOG。</li>
 */
@Getter
@Setter
public class ProductCapabilityDimension {
    /** 可配维度行标识 */
    private Long capabilityDimensionId;

    /** 所属可开通项代码 */
    private String capabilityCode;

    /** 维度，须可用于开通接受条件 */
    private PricingDimension dimensionCode;

    /** 合同开通时是否必须给出该维度条件 */
    private Boolean required;

    /** 记录创建的 UTC 微秒时间 */
    private Instant createTime;

    /** 记录最近一次持久化更新的 UTC 微秒时间 */
    private Instant updateTime;
}
