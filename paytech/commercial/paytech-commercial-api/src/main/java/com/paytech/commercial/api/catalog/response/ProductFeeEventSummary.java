package com.paytech.commercial.api.catalog.response;

import lombok.Getter;
import lombok.Setter;

/** 产品目录 L2 计费事件一行，字段与初始化 DML t_product_fee_event 一致（不含技术时间）。 */
@Getter
@Setter
public class ProductFeeEventSummary {
    /** 计费事件登记 ID */
    private String productFeeEventId;

    /** 所属产品代码 */
    private String productCode;

    /** 计费事件代码（开放目录代码，如 SALE），价格组件的 triggerCode */
    private String feeEventCode;

    /** 登记该事件的条款类型（开放目录代码） */
    private String termType;

    /** 计费事实类型（开放目录代码） */
    private String billingFactType;
}
