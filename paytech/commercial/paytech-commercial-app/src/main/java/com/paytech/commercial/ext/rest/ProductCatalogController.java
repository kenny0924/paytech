package com.paytech.commercial.ext.rest;

import static com.paytech.commercial.ext.rest.WireValues.code;
import static com.paytech.commercial.ext.rest.WireValues.matching;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.commercial.api.catalog.ProductCatalogApi;
import com.paytech.commercial.api.catalog.request.CreateProductCapabilityRequestBody;
import com.paytech.commercial.api.catalog.request.ProductCatalogTransitionRequestBody;
import com.paytech.commercial.api.catalog.request.UpdateFeeEventCapabilityRequestBody;
import com.paytech.commercial.api.catalog.request.UpdateProductCapabilityRequestBody;
import com.paytech.commercial.api.catalog.response.PricingDimensionListResponseBody;
import com.paytech.commercial.api.catalog.response.ProductCapabilitySummary;
import com.paytech.commercial.api.catalog.response.ProductFeeEventSummary;
import com.paytech.commercial.api.catalog.response.ProductCapabilityListResponseBody;
import com.paytech.commercial.api.catalog.response.ProductFeeEventListResponseBody;
import com.paytech.commercial.api.catalog.response.ProductListResponseBody;
import com.paytech.commercial.api.enums.ProductCode;
import com.paytech.commercial.api.enums.ProductStatus;
import com.paytech.commercial.domainservice.service.ProductCatalogQueryService;
import com.paytech.commercial.service.processor.CreateProductCapabilityProcessor;
import com.paytech.commercial.service.processor.TransitionProductCapabilityProcessor;
import com.paytech.commercial.service.processor.UpdateFeeEventCapabilityProcessor;
import com.paytech.commercial.service.processor.UpdateProductCapabilityProcessor;

import lombok.AllArgsConstructor;

/** 产品目录接口的 HTTP 协议适配：查询直接委托 ProductCatalogQueryService，目录维护委托各写入 Processor。 */
@RestController
@AllArgsConstructor
public class ProductCatalogController implements ProductCatalogApi {
    private final ProductCatalogQueryService productCatalogQueryService;
    private final CreateProductCapabilityProcessor createCapabilityProcessor;
    private final UpdateProductCapabilityProcessor updateCapabilityProcessor;
    private final UpdateFeeEventCapabilityProcessor updateFeeEventCapabilityProcessor;
    private final TransitionProductCapabilityProcessor transitionCapabilityProcessor;

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

    /** 维度清单（代码登记）。 */
    @Override
    public PricingDimensionListResponseBody listDimensions() {
        return ProductCatalogConverter.dimensions();
    }

    /** 新增可开通项，返回 201。 */
    @Override
    public ResponseEntity<ProductCapabilitySummary> createCapability(String actorId, CreateProductCapabilityRequestBody body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ProductCatalogConverter.capability(
            createCapabilityProcessor.process(ProductCatalogConverter.toRequest(actorId, body))));
    }

    /** 改写可开通项定义。 */
    @Override
    public ProductCapabilitySummary updateCapability(String actorId, String productCapabilityId,
        UpdateProductCapabilityRequestBody body) {
        return ProductCatalogConverter.capability(updateCapabilityProcessor.process(
            ProductCatalogConverter.toRequest(actorId, productCapabilityId, body)));
    }

    /** 停用可开通项。 */
    @Override
    public ProductCapabilitySummary retireCapability(String actorId, String productCapabilityId,
        ProductCatalogTransitionRequestBody body) {
        return ProductCatalogConverter.capability(transitionCapabilityProcessor.process(
            ProductCatalogConverter.toRequest(actorId, productCapabilityId, body, ProductStatus.RETIRED)));
    }

    /** 启用可开通项。 */
    @Override
    public ProductCapabilitySummary activateCapability(String actorId, String productCapabilityId,
        ProductCatalogTransitionRequestBody body) {
        return ProductCatalogConverter.capability(transitionCapabilityProcessor.process(
            ProductCatalogConverter.toRequest(actorId, productCapabilityId, body, ProductStatus.ACTIVE)));
    }

    /** 改写计费事件挂接。 */
    @Override
    public ProductFeeEventSummary updateFeeEventCapability(String actorId, String productFeeEventId,
        UpdateFeeEventCapabilityRequestBody body) {
        return ProductCatalogConverter.feeEvent(updateFeeEventCapabilityProcessor.process(
            ProductCatalogConverter.toRequest(actorId, productFeeEventId, body)));
    }
}
