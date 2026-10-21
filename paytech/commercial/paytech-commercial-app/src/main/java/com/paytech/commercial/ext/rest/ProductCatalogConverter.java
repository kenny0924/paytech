package com.paytech.commercial.ext.rest;

import static com.paytech.commercial.ext.rest.WireValues.code;
import static com.paytech.commercial.ext.rest.WireValues.id;
import static com.paytech.commercial.ext.rest.WireValues.text;

import java.util.List;

import com.paytech.commercial.api.catalog.request.CapabilityDimensionEntry;
import com.paytech.commercial.api.catalog.request.CreateProductCapabilityRequestBody;
import com.paytech.commercial.api.catalog.request.ProductCatalogTransitionRequestBody;
import com.paytech.commercial.api.catalog.request.UpdateFeeEventCapabilityRequestBody;
import com.paytech.commercial.api.catalog.request.UpdateProductCapabilityRequestBody;
import com.paytech.commercial.api.catalog.response.CapabilityDimensionDetail;
import com.paytech.commercial.api.catalog.response.PricingDimensionListResponseBody;
import com.paytech.commercial.api.catalog.response.PricingDimensionSummary;
import com.paytech.commercial.api.catalog.response.ProductCapabilityListResponseBody;
import com.paytech.commercial.api.catalog.response.ProductCapabilitySummary;
import com.paytech.commercial.api.catalog.response.ProductFeeEventListResponseBody;
import com.paytech.commercial.api.catalog.response.ProductFeeEventSummary;
import com.paytech.commercial.api.catalog.response.ProductListResponseBody;
import com.paytech.commercial.api.catalog.response.ProductSummary;
import com.paytech.commercial.api.enums.CapabilityKind;
import com.paytech.commercial.api.enums.PricingDimension;
import com.paytech.commercial.api.enums.ProductCode;
import com.paytech.commercial.api.enums.ProductStatus;
import com.paytech.commercial.domain.ProductCapabilityView;
import com.paytech.commercial.domain.ProductFeeEventView;
import com.paytech.commercial.domain.ProductView;
import com.paytech.commercial.service.processor.request.CreateProductCapabilityRequest;
import com.paytech.commercial.service.processor.request.ProductCapabilityDefinitionRequest;
import com.paytech.commercial.service.processor.request.TransitionProductCapabilityRequest;
import com.paytech.commercial.service.processor.request.UpdateFeeEventCapabilityRequest;
import com.paytech.commercial.service.processor.request.UpdateProductCapabilityRequest;

/** 产品目录 Wire 与内部请求 / 结果的协议转换。 */
final class ProductCatalogConverter {
    /** 纯静态工具。 */
    private ProductCatalogConverter() {
    }

    /** 产品列表。 */
    static ProductListResponseBody products(List<ProductView> views) {
        ProductListResponseBody body = new ProductListResponseBody();
        body.setProducts(views.stream().map(view -> {
            ProductSummary summary = new ProductSummary();
            summary.setProductId(Long.toString(view.getProductId()));
            summary.setProductCode(text(view.getProductCode()));
            summary.setProductName(view.getProductName());
            summary.setBusinessModeScope(text(view.getBusinessModeScope()));
            summary.setBaseProduct(view.isBaseProduct());
            summary.setStatus(text(view.getStatus()));
            return summary;
        }).toList());
        return body;
    }

    /** 可开通项列表。 */
    static ProductCapabilityListResponseBody capabilities(List<ProductCapabilityView> views) {
        ProductCapabilityListResponseBody body = new ProductCapabilityListResponseBody();
        body.setCapabilities(views.stream().map(ProductCatalogConverter::capability).toList());
        return body;
    }

    /** 一个可开通项与其可配维度；废弃维度字段恒为 null。 */
    static ProductCapabilitySummary capability(ProductCapabilityView view) {
        ProductCapabilitySummary summary = new ProductCapabilitySummary();
        summary.setProductCapabilityId(Long.toString(view.getProductCapabilityId()));
        summary.setProductCode(text(view.getProductCode()));
        summary.setCapabilityCode(view.getCapabilityCode());
        summary.setCapabilityName(view.getCapabilityName());
        summary.setCapabilityKind(text(view.getCapabilityKind()));
        summary.setPaymentMethodCode(view.getPaymentMethodCode());
        summary.setDimensionCode(null);
        summary.setDimensionValue(null);
        summary.setStatus(text(view.getStatus()));
        summary.setChangeVersion(view.getChangeVersion());
        summary.setDimensions(view.getDimensions().stream().map(dimension -> {
            CapabilityDimensionDetail detail = new CapabilityDimensionDetail();
            detail.setDimensionCode(dimension.getDimensionCode().getCode());
            detail.setRequired(dimension.isRequired());
            return detail;
        }).toList());
        return summary;
    }

    /** 维度清单：按 PricingDimension 登记顺序。 */
    static PricingDimensionListResponseBody dimensions() {
        PricingDimensionListResponseBody body = new PricingDimensionListResponseBody();
        body.setDimensions(java.util.Arrays.stream(PricingDimension.values()).map(dimension -> {
            PricingDimensionSummary summary = new PricingDimensionSummary();
            summary.setDimensionCode(dimension.getCode());
            summary.setDisplayName(dimension.getDisplayName());
            summary.setFactSource(dimension.getFactSource().getCode());
            summary.setEvaluationPoint(dimension.getEvaluationPoint().getCode());
            summary.setAcceptanceAllowed(dimension.isAcceptanceAllowed());
            summary.setPricingAllowed(dimension.isPricingAllowed());
            return summary;
        }).toList());
        return body;
    }

    /** 新增可开通项请求。 */
    static CreateProductCapabilityRequest toRequest(String actorId, CreateProductCapabilityRequestBody body) {
        CreateProductCapabilityRequest request = new CreateProductCapabilityRequest();
        definition(request, actorId, body.getCapabilityName(), body.getProductCode(), body.getDimensions());
        request.setCapabilityCode(body.getCapabilityCode());
        request.setCapabilityKind(code(CapabilityKind.class, "capabilityKind", body.getCapabilityKind()));
        request.setPaymentMethodCode(body.getPaymentMethodCode());
        return request;
    }

    /** 改写可开通项请求。 */
    static UpdateProductCapabilityRequest toRequest(String actorId, String productCapabilityId,
        UpdateProductCapabilityRequestBody body) {
        UpdateProductCapabilityRequest request = new UpdateProductCapabilityRequest();
        definition(request, actorId, body.getCapabilityName(), body.getProductCode(), body.getDimensions());
        request.setProductCapabilityId(id("productCapabilityId", productCapabilityId));
        request.setExpectedChangeVersion(body.getExpectedChangeVersion());
        return request;
    }

    /** 停用 / 启用请求。 */
    static TransitionProductCapabilityRequest toRequest(String actorId, String productCapabilityId,
        ProductCatalogTransitionRequestBody body, ProductStatus target) {
        return new TransitionProductCapabilityRequest(AgreementConverter.actor(actorId),
            id("productCapabilityId", productCapabilityId), body.getExpectedChangeVersion(), target);
    }

    /** 改写计费事件挂接请求。 */
    static UpdateFeeEventCapabilityRequest toRequest(String actorId, String productFeeEventId,
        UpdateFeeEventCapabilityRequestBody body) {
        return new UpdateFeeEventCapabilityRequest(AgreementConverter.actor(actorId), id("productFeeEventId", productFeeEventId),
            body.getExpectedChangeVersion(), code(CapabilityKind.class, "capabilityKind", body.getCapabilityKind()),
            body.getCapabilityCode());
    }

    /** 新增与改写共用的定义字段。 */
    static void definition(ProductCapabilityDefinitionRequest request, String actorId, String capabilityName,
        String productCode, List<CapabilityDimensionEntry> dimensions) {
        request.setActorId(AgreementConverter.actor(actorId));
        request.setCapabilityName(capabilityName);
        request.setProductCode(code(ProductCode.class, "productCode", productCode));
        request.setDimensions(dimensions.stream().map(entry -> {
            ProductCapabilityDefinitionRequest.Dimension dimension = new ProductCapabilityDefinitionRequest.Dimension();
            dimension.setDimensionCode(code(PricingDimension.class, "dimensionCode", entry.getDimensionCode()));
            dimension.setRequired(Boolean.TRUE.equals(entry.getRequired()));
            return dimension;
        }).toList());
    }

    /** 计费事件列表。 */
    static ProductFeeEventListResponseBody feeEvents(List<ProductFeeEventView> views) {
        ProductFeeEventListResponseBody body = new ProductFeeEventListResponseBody();
        body.setFeeEvents(views.stream().map(ProductCatalogConverter::feeEvent).toList());
        return body;
    }

    /** 一个计费事件与其挂接。 */
    static ProductFeeEventSummary feeEvent(ProductFeeEventView view) {
        ProductFeeEventSummary summary = new ProductFeeEventSummary();
        summary.setProductFeeEventId(Long.toString(view.getProductFeeEventId()));
        summary.setProductCode(text(view.getProductCode()));
        summary.setFeeEventCode(view.getFeeEventCode());
        summary.setTermType(view.getTermType());
        summary.setBillingFactType(view.getBillingFactType());
        summary.setCapabilityKind(text(view.getCapabilityKind()));
        summary.setCapabilityCode(view.getCapabilityCode());
        summary.setChangeVersion(view.getChangeVersion());
        return summary;
    }
}
