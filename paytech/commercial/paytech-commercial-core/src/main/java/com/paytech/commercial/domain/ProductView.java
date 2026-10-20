package com.paytech.commercial.domain;

import com.paytech.commercial.api.enums.ProductBusinessModeScope;
import com.paytech.commercial.api.enums.ProductCode;
import com.paytech.commercial.api.enums.ProductStatus;
import com.paytech.commercial.core.dal.dataobject.Product;

import lombok.Getter;

/** 产品目录 L0 一行的只读结果，供 app 入站适配器转换；不携带 DataObject。 */
@Getter
public class ProductView {
    /** 产品 ID */
    private final long productId;

    /** 产品代码 */
    private final ProductCode productCode;

    /** 产品名称 */
    private final String productName;

    /** 适用业务模式 */
    private final ProductBusinessModeScope businessModeScope;

    /** 是否基座产品 */
    private final boolean baseProduct;

    /** 状态 */
    private final ProductStatus status;

    /**
     * 由目录行构造
     *
     * @param product 目录行
     */
    public ProductView(Product product) {
        this.productId = product.getProductId();
        this.productCode = product.getProductCode();
        this.productName = product.getProductName();
        this.businessModeScope = product.getBusinessModeScope();
        this.baseProduct = Boolean.TRUE.equals(product.getBaseProduct());
        this.status = product.getStatus();
    }
}
