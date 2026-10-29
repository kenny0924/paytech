package com.paytech.client.master.test.mock.external.commercial;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.paytech.client.master.test.mocker.commercial.CommercialProductCatalogMocker;
import com.paytech.commercial.api.catalog.ProductCatalogApi;
import com.paytech.commercial.api.catalog.request.CreateProductCapabilityRequestBody;
import com.paytech.commercial.api.catalog.request.ProductCatalogTransitionRequestBody;
import com.paytech.commercial.api.catalog.request.UpdateFeeEventCapabilityRequestBody;
import com.paytech.commercial.api.catalog.request.UpdateProductCapabilityRequestBody;
import com.paytech.commercial.api.catalog.response.PricingDimensionListResponseBody;
import com.paytech.commercial.api.catalog.response.ProductCapabilityListResponseBody;
import com.paytech.commercial.api.catalog.response.ProductCapabilitySummary;
import com.paytech.commercial.api.catalog.response.ProductFeeEventListResponseBody;
import com.paytech.commercial.api.catalog.response.ProductFeeEventSummary;
import com.paytech.commercial.api.catalog.response.ProductListResponseBody;
import com.paytech.foundation.testsupport.api.MockHttpFault;

/** commercial 产品能力目录的测试 HTTP 实现：只有可开通项列表按本例配置返回，其余方法均为意外调用。 */
@RestController
@TestComponent
public class MockCommercialProductCatalogApi implements ProductCatalogApi {
    private final CommercialProductCatalogMocker mocker;

    /** 与用例共享配置与记录。 */
    public MockCommercialProductCatalogApi(CommercialProductCatalogMocker mocker) {
        this.mocker = mocker;
    }

    /** 返回本例配置的可开通项列表或故障；未配置为意外调用。 */
    @Override
    public ProductCapabilityListResponseBody listCapabilities(String productCode, String status) {
        var attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        attributes.getResponse().setHeader("X-Request-Id", "client-master-commercial-test");
        var reply = mocker.listCapabilities(productCode, status);
        if (reply == null) {
            throw unexpected();
        }
        if (reply.disconnect()) {
            throw MockHttpFault.disconnect();
        }
        if (reply.problem() != null) {
            throw MockHttpFault.json(reply.status(), reply.problem());
        }
        return reply.body();
    }

    @Override
    public ProductListResponseBody listProducts(String status) {
        throw unexpected();
    }

    @Override
    public ProductFeeEventListResponseBody listFeeEvents(String productCode, String termType) {
        throw unexpected();
    }

    @Override
    public PricingDimensionListResponseBody listDimensions() {
        throw unexpected();
    }

    @Override
    public ResponseEntity<ProductCapabilitySummary> createCapability(String actorId, CreateProductCapabilityRequestBody body) {
        throw unexpected();
    }

    @Override
    public ProductCapabilitySummary updateCapability(String actorId, String productCapabilityId, UpdateProductCapabilityRequestBody body) {
        throw unexpected();
    }

    @Override
    public ProductCapabilitySummary retireCapability(String actorId, String productCapabilityId, ProductCatalogTransitionRequestBody body) {
        throw unexpected();
    }

    @Override
    public ProductCapabilitySummary activateCapability(String actorId, String productCapabilityId,
        ProductCatalogTransitionRequestBody body) {
        throw unexpected();
    }

    @Override
    public ProductFeeEventSummary updateFeeEventCapability(String actorId, String productFeeEventId,
        UpdateFeeEventCapabilityRequestBody body) {
        throw unexpected();
    }

    /** 未配置或未模拟的方法：Mock 服务记录为意外调用。 */
    static IllegalStateException unexpected() {
        return new IllegalStateException("未配置的 commercial 产品目录调用");
    }
}
