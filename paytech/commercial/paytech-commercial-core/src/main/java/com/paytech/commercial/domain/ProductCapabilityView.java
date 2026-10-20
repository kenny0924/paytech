package com.paytech.commercial.domain;

import com.paytech.commercial.api.enums.CapabilityKind;
import com.paytech.commercial.api.enums.PricingDimension;
import com.paytech.commercial.api.enums.ProductCode;
import com.paytech.commercial.api.enums.ProductStatus;
import com.paytech.commercial.core.dal.dataobject.ProductCapability;

import lombok.Getter;

/** 产品目录 L1 可开通项一行的只读结果，供 app 入站适配器转换；不携带 DataObject。 */
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

    /** 细分维度，可空 */
    private final PricingDimension dimensionCode;

    /** 细分维度值，可空 */
    private final String dimensionValue;

    /** 状态 */
    private final ProductStatus status;

    /**
     * 由目录行构造
     *
     * @param capability 目录行
     */
    public ProductCapabilityView(ProductCapability capability) {
        this.productCapabilityId = capability.getProductCapabilityId();
        this.productCode = capability.getProductCode();
        this.capabilityCode = capability.getCapabilityCode();
        this.capabilityName = capability.getCapabilityName();
        this.capabilityKind = capability.getCapabilityKind();
        this.paymentMethodCode = capability.getPaymentMethodCode();
        this.dimensionCode = capability.getDimensionCode();
        this.dimensionValue = capability.getDimensionValue();
        this.status = capability.getStatus();
    }
}
