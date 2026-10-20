package com.paytech.commercial.ext.rest;

import static com.paytech.commercial.ext.rest.WireValues.code;
import static com.paytech.commercial.ext.rest.WireValues.matching;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.commercial.api.catalog.ProductCatalogApi;
import com.paytech.commercial.api.catalog.response.ProductCapabilityListResponseBody;
import com.paytech.commercial.api.catalog.response.ProductFeeEventListResponseBody;
import com.paytech.commercial.api.catalog.response.ProductListResponseBody;
import com.paytech.commercial.api.enums.ProductCode;
import com.paytech.commercial.api.enums.ProductStatus;
import com.paytech.commercial.domainservice.service.ProductCatalogQueryService;

import lombok.AllArgsConstructor;

/** 产品目录只读接口的 HTTP 协议适配：单表查询，直接委托 ProductCatalogQueryService。 */
@RestController
@AllArgsConstructor
public class ProductCatalogController implements ProductCatalogApi {
    private final ProductCatalogQueryService productCatalogQueryService;

    /** 产品列表。 */
    @Override
    public ProductListResponseBody listProducts(String status) {
        return ProductCatalogConverter.products(
            productCatalogQueryService.listProducts(code(ProductStatus.class, "status", status)));
    }

    /** 可开通项列表。 */
    @Override
    public ProductCapabilityListResponseBody listCapabilities(String productCode, String status) {
        return ProductCatalogConverter.capabilities(productCatalogQueryService.listCapabilities(
            code(ProductCode.class, "productCode", productCode), code(ProductStatus.class, "status", status)));
    }

    /** 计费事件列表。 */
    @Override
    public ProductFeeEventListResponseBody listFeeEvents(String productCode, String termType) {
        return ProductCatalogConverter.feeEvents(productCatalogQueryService.listFeeEvents(
            code(ProductCode.class, "productCode", productCode),
            matching("termType", termType, CommercialTermQueryConverter.TERM_TYPE)));
    }
}
