package com.paytech.commercial.domain;

import java.util.Comparator;
import java.util.List;

import com.paytech.commercial.api.enums.CapabilityKind;
import com.paytech.commercial.api.enums.PricingDimension;
import com.paytech.commercial.api.enums.ProductCode;
import com.paytech.commercial.api.enums.ProductStatus;
import com.paytech.commercial.core.dal.dataobject.ProductCapability;
import com.paytech.commercial.core.dal.dataobject.ProductCapabilityDimension;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 产品目录 L1 可开通项一行与其可配维度的结果，供 app 入站适配器转换；不携带 DataObject。 */
@Getter
public class ProductCapabilityView {
    /** 可开通项 ID */
    private final long productCapabilityId;

    /** 所属产品代码 */
    private final ProductCode productCode;

    /** 可开通项代码 */
    private final String capabilityCode;

    /** 可开通项名称 */
    private final String capabilityName;

    /** 能力种类 */
    private final CapabilityKind capabilityKind;

    /** 支付方式代码，可空 */
    private final String paymentMethodCode;

    /** 已废弃的细分维度，恒为空 */
    private final PricingDimension dimensionCode;

    /** 已废弃的细分维度值，恒为空 */
    private final String dimensionValue;

    /** 状态 */
    private final ProductStatus status;

    /** 目录行版本 */
    private final long changeVersion;

    /** 可配维度，按维度代码排序 */
    private final List<Dimension> dimensions;

    /**
     * 由目录行与其可配维度构造
     *
     * @param capability 目录行
     * @param dimensions 该可开通项的可配维度
     */
    public ProductCapabilityView(ProductCapability capability, List<ProductCapabilityDimension> dimensions) {
        this.productCapabilityId = capability.getProductCapabilityId();
        this.productCode = capability.getProductCode();
        this.capabilityCode = capability.getCapabilityCode();
        this.capabilityName = capability.getCapabilityName();
        this.capabilityKind = capability.getCapabilityKind();
        this.paymentMethodCode = capability.getPaymentMethodCode();
        this.dimensionCode = capability.getDimensionCode();
        this.dimensionValue = capability.getDimensionValue();
        this.status = capability.getStatus();
        this.changeVersion = capability.getChangeVersion();
        this.dimensions = dimensions.stream().filter(item -> item.getCapabilityCode().equals(capability.getCapabilityCode()))
            .sorted(Comparator.comparing(item -> item.getDimensionCode().getCode()))
            .map(item -> new Dimension(item.getDimensionCode(), Boolean.TRUE.equals(item.getRequired()))).toList();
    }

    /** 一个可配维度。 */
    @Getter
    @AllArgsConstructor
    public static class Dimension {
        /** 维度 */
        private final PricingDimension dimensionCode;

        /** 合同开通时是否必填 */
        private final boolean required;
    }
}
