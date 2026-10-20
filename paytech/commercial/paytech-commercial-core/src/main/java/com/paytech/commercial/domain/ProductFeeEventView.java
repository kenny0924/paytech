package com.paytech.commercial.domain;

import com.paytech.commercial.api.enums.ProductCode;
import com.paytech.commercial.core.dal.dataobject.ProductFeeEvent;

import lombok.Getter;

/** 产品目录 L2 计费事件一行的只读结果，供 app 入站适配器转换；不携带 DataObject。 */
@Getter
public class ProductFeeEventView {
    /** 计费事件登记 ID */
    private final long productFeeEventId;

    /** 所属产品代码 */
    private final ProductCode productCode;

    /** 计费事件代码 */
    private final String feeEventCode;

    /** 条款类型 */
    private final String termType;

    /** 计费事实类型 */
    private final String billingFactType;

    /**
     * 由目录行构造
     *
     * @param feeEvent 目录行
     */
    public ProductFeeEventView(ProductFeeEvent feeEvent) {
        this.productFeeEventId = feeEvent.getProductFeeEventId();
        this.productCode = feeEvent.getProductCode();
        this.feeEventCode = feeEvent.getFeeEventCode();
        this.termType = feeEvent.getTermType();
        this.billingFactType = feeEvent.getBillingFactType();
    }
}
