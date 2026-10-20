package com.paytech.commercial.api.catalog.response;

import lombok.Getter;
import lombok.Setter;

/** 产品目录 L1 可开通项一行，字段与初始化 DML t_product_capability 一致（不含技术时间）。 */
@Getter
@Setter
public class ProductCapabilitySummary {
    /** 可开通项 ID */
    private String productCapabilityId;

    /** 所属产品代码 */
    private String productCode;

    /** 可开通项代码（开放目录代码，如 CARD_VISA） */
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

    /**
     * 附加维度代码
     * <li>例如 CARD_BRAND；没有维度为空。</li>
     */
    private String dimensionCode;

    /** 附加维度值，与 dimensionCode 同时出现 */
    private String dimensionValue;

    /**
     * 状态
     * <li>DRAFT、ACTIVE、RETIRED。</li>
     */
    private String status;
}
