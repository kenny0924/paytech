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
 * <li>运营维护，写入以 changeVersion 做 CAS，BCR 聚合根 PRODUCT_CATALOG；capability_code 与 capabilityKind 创建后不可修改。</li>
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
     * 已废弃的细分维度
     * <li>恒为 NULL；细分改为合同开通项接受条件，列保留。</li>
     */
    private PricingDimension dimensionCode;

    /** 已废弃的细分维度值，恒为 NULL */
    private String dimensionValue;

    /**
     * 生命周期状态
     * <li>合同引用时须为 ACTIVE。</li>
     */
    private ProductStatus status;

    /** 目录行乐观锁版本，初始 1，每次写入加一 */
    private Long changeVersion;

    /** 记录创建的 UTC 微秒时间 */
    private Instant createTime;

    /** 记录最近一次持久化更新的 UTC 微秒时间 */
    private Instant updateTime;
}
