package com.paytech.commercial.api.catalog.response;

import lombok.Getter;
import lombok.Setter;

/** 产品目录 L0 一行，字段与初始化 DML t_product 一致（不含技术时间）。 */
@Getter
@Setter
public class ProductSummary {
    /** 产品 ID */
    private String productId;

    /** 产品代码 */
    private String productCode;

    /** 产品名称 */
    private String productName;

    /**
     * 适用业务模式
     * <li>MOR、PSP、BOTH。</li>
     */
    private String businessModeScope;

    /**
     * 是否基础产品
     * <li>基础产品随合同默认具备，不单独订阅。</li>
     */
    private Boolean baseProduct;

    /**
     * 状态
     * <li>DRAFT、ACTIVE、RETIRED。</li>
     */
    private String status;
}
