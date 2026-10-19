package com.paytech.commercial.core.dal.dataobject;

import java.time.Instant;

import com.paytech.commercial.api.enums.CapabilityKind;
import com.paytech.commercial.api.enums.PricingDimension;
import com.paytech.commercial.api.enums.ProductCode;
import com.paytech.commercial.api.enums.ProductStatus;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * t_product_capability：L1 可开通项目录。
 * <li>随产品发版维护，不接入 BCR；capability_code 全局唯一稳定。</li>
 */
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class ProductCapability {
    /** 可开通项目录行的内部唯一标识 */
    private Long productCapabilityId;

    /** 所属产品的稳定代码 */
    private ProductCode productCode;

    /**
     * 全局唯一且稳定的可开通项代码，uk_product_capability_code
     * <li>开放目录，稳定项见 CommercialCodes.Capability。</li>
     */
    private String capabilityCode;

    /** 供 Operations 识别可开通项的展示名称 */
    private String capabilityName;

    /** 能力种类，决定是否绑定 Payment Method */
    private CapabilityKind capabilityKind;

    /**
     * Payment 拥有的 Payment Method 稳定代码
     * <li>capability_kind=PAYMENT_METHOD 时必填，其他种类为空。</li>
     */
    private String paymentMethodCode;

    /**
     * 把开通项细分到画像维度的单一条件维度
     * <li>与 dimensionValue 成组为空或非空。</li>
     */
    private PricingDimension dimensionCode;

    /** 细分维度的单个规范化值，如 AMEX */
    private String dimensionValue;

    /**
     * 生命周期状态
     * <li>合同引用时须为 ACTIVE。</li>
     */
    private ProductStatus status;

    /** 记录创建的 UTC 微秒时间 */
    private Instant createTime;

    /** 记录最近一次持久化更新的 UTC 微秒时间 */
    private Instant updateTime;
}
