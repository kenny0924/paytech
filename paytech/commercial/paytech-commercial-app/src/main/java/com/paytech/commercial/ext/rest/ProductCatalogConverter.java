package com.paytech.commercial.ext.rest;

import static com.paytech.commercial.ext.rest.WireValues.text;

import java.util.List;

import com.paytech.commercial.api.catalog.response.ProductCapabilityListResponseBody;
import com.paytech.commercial.api.catalog.response.ProductCapabilitySummary;
import com.paytech.commercial.api.catalog.response.ProductFeeEventListResponseBody;
import com.paytech.commercial.api.catalog.response.ProductFeeEventSummary;
import com.paytech.commercial.api.catalog.response.ProductListResponseBody;
import com.paytech.commercial.api.catalog.response.ProductSummary;
import com.paytech.commercial.domain.ProductCapabilityView;
import com.paytech.commercial.domain.ProductFeeEventView;
import com.paytech.commercial.domain.ProductView;

/** 产品目录只读结果到 Wire 的协议转换。 */
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
        body.setCapabilities(views.stream().map(view -> {
            ProductCapabilitySummary summary = new ProductCapabilitySummary();
            summary.setProductCapabilityId(Long.toString(view.getProductCapabilityId()));
            summary.setProductCode(text(view.getProductCode()));
            summary.setCapabilityCode(view.getCapabilityCode());
            summary.setCapabilityName(view.getCapabilityName());
            summary.setCapabilityKind(text(view.getCapabilityKind()));
            summary.setPaymentMethodCode(view.getPaymentMethodCode());
            summary.setDimensionCode(text(view.getDimensionCode()));
            summary.setDimensionValue(view.getDimensionValue());
            summary.setStatus(text(view.getStatus()));
            return summary;
        }).toList());
        return body;
    }

    /** 计费事件列表。 */
    static ProductFeeEventListResponseBody feeEvents(List<ProductFeeEventView> views) {
        ProductFeeEventListResponseBody body = new ProductFeeEventListResponseBody();
        body.setFeeEvents(views.stream().map(view -> {
            ProductFeeEventSummary summary = new ProductFeeEventSummary();
            summary.setProductFeeEventId(Long.toString(view.getProductFeeEventId()));
            summary.setProductCode(text(view.getProductCode()));
            summary.setFeeEventCode(view.getFeeEventCode());
            summary.setTermType(view.getTermType());
            summary.setBillingFactType(view.getBillingFactType());
            return summary;
        }).toList());
        return body;
    }
}
