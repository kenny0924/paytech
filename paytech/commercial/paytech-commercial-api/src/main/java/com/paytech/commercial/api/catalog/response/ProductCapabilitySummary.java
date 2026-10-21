package com.paytech.commercial.api.catalog.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 产品目录 L1 可开通项一行，字段与 t_product_capability 一致（不含技术时间），含可配维度。 */
@Getter
@Setter
public class ProductCapabilitySummary {
    /** 可开通项 ID */
    private String productCapabilityId;

    /** 所属产品代码 */
    private String productCode;

    /** 可开通项代码（开放目录代码，如 CARD） */
    private String capabilityCode;

    /** 可开通项名称 */
    private String capabilityName;

    /** 可开通项种类 */
    private String capabilityKind;

    /**
     * 支付方式代码
     * <li>非支付方式类可开通项为空。</li>
     */
    private String paymentMethodCode;

    /** 已废弃，恒为 null（细分维度改为合同开通项接受条件） */
    private String dimensionCode;

    /** 已废弃，恒为 null */
    private String dimensionValue;

    /** 可配维度，按维度代码排序 */
    private List<CapabilityDimensionDetail> dimensions;

    /**
     * 状态
     * <li>DRAFT、ACTIVE、RETIRED。</li>
     */
    private String status;

    /** 目录行版本，下一次写入的 expectedChangeVersion */
    private Long changeVersion;
}
