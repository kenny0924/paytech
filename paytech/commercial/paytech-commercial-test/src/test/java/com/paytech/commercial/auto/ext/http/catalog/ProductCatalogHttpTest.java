package com.paytech.commercial.auto.ext.http.catalog;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.commercial.api.catalog.ProductCatalogApi;
import com.paytech.commercial.api.constant.CommercialCodes;
import com.paytech.commercial.api.enums.ProductCode;
import com.paytech.commercial.core.dal.dataobject.Product;
import com.paytech.commercial.core.dal.dataobject.ProductCapability;
import com.paytech.commercial.core.dal.dataobject.ProductFeeEvent;
import com.paytech.commercial.test.base.write.AbstractCommercialWriteApiTest;
import com.paytech.commercial.test.mocker.local.catalog.ProductCatalogFixture;
import com.paytech.foundation.common.enums.CodeEnum;

import tools.jackson.databind.JsonNode;

/**
 * 产品目录只读接口（运营控制台第二切片 technique.md §2）：三张目录表的全集与初始化 DML 种子固件逐字段一致，筛选、空结果与参数校验
 *
 * @see ProductCatalogApi
 */
class ProductCatalogHttpTest extends AbstractCommercialWriteApiTest {
    private static final String PATH = "/products/v1";

    private ProductCatalogFixture seed;

    /** 每例：插入种子目录（与 commercial-initial-data.sql 同内容）。 */
    @BeforeEach
    void setUpClassMocks() {
        seed = catalog.mockSeedCatalog();
    }

    /** 无筛选：三表全集按 ID 升序，逐字段等于种子固件。 */
    @Test
    void test_normalFlow() {
        JsonNode products = get(PATH).assertOk().node("products");
        JsonNode capabilities = get(PATH + "/capabilities").assertOk().node("capabilities");
        JsonNode feeEvents = get(PATH + "/fee-events").assertOk().node("feeEvents");

        assertAll(() -> assertEquals(seed.products().stream().map(ProductCatalogHttpTest::row).map(json::readTree).toList(),
                products.valueStream().toList()),
            () -> assertEquals(seed.capabilities().stream().map(ProductCatalogHttpTest::row).map(json::readTree).toList(),
                capabilities.valueStream().toList()),
            () -> assertEquals(seed.feeEvents().stream().map(ProductCatalogHttpTest::row).map(json::readTree).toList(),
                feeEvents.valueStream().toList()));
    }

    /** 产品代码、状态与条款类型筛选。 */
    @Test
    void test_filters() {
        List<String> acquiringCapabilities = seed.capabilities().stream()
            .filter(row -> row.getProductCode() == ProductCode.ACQUIRING).map(ProductCapability::getCapabilityCode).toList();
        List<String> clientPricingEvents = seed.feeEvents().stream()
            .filter(row -> CommercialCodes.TermType.CLIENT_PRICING.equals(row.getTermType()))
            .map(ProductFeeEvent::getFeeEventCode).toList();
        List<String> payoutEvents = seed.feeEvents().stream().filter(row -> row.getProductCode() == ProductCode.PAYOUT)
            .map(ProductFeeEvent::getFeeEventCode).toList();

        assertAll(() -> assertEquals(seed.products().size(), get(PATH + "?status=ACTIVE").assertOk().node("products").size()),
            () -> assertEquals(0, get(PATH + "?status=RETIRED").assertOk().node("products").size()),
            () -> assertEquals(acquiringCapabilities, codes(get(PATH + "/capabilities?productCode=ACQUIRING&status=ACTIVE")
                .assertOk().node("capabilities"), "capabilityCode")),
            () -> assertEquals(clientPricingEvents, codes(get(PATH + "/fee-events?termType=CLIENT_PRICING").assertOk()
                .node("feeEvents"), "feeEventCode")),
            () -> assertEquals(payoutEvents, codes(get(PATH + "/fee-events?productCode=PAYOUT").assertOk()
                .node("feeEvents"), "feeEventCode")));
    }

    /** 未知产品代码 / 状态与非法条款类型格式 400；合法但无匹配的条款类型为空列表。 */
    @Test
    void test_invalidParameter() {
        get(PATH + "?status=ENABLED").assertInvalidRequest();
        get(PATH + "/capabilities?productCode=CARD").assertInvalidRequest();
        get(PATH + "/fee-events?termType=client-pricing").assertInvalidRequest();
        assertEquals(0, get(PATH + "/fee-events?termType=UNREGISTERED_TERM").assertOk().node("feeEvents").size());
    }

    /** 种子产品的期望 JSON（按树比较，与字段顺序无关）。 */
    private static String row(Product product) {
        return "{\"productId\":\"" + product.getProductId() + "\",\"productCode\":\"" + product.getProductCode().getCode()
            + "\",\"productName\":\"" + product.getProductName() + "\",\"businessModeScope\":\""
            + product.getBusinessModeScope().getCode() + "\",\"baseProduct\":" + product.getBaseProduct()
            + ",\"status\":" + quoted(product.getStatus()) + "}";
    }

    /** 种子可开通项的期望 JSON（按树比较）。 */
    private static String row(ProductCapability capability) {
        return "{\"productCapabilityId\":\"" + capability.getProductCapabilityId() + "\",\"productCode\":\""
            + capability.getProductCode().getCode() + "\",\"capabilityCode\":\"" + capability.getCapabilityCode()
            + "\",\"capabilityName\":\"" + capability.getCapabilityName() + "\",\"capabilityKind\":"
            + quoted(capability.getCapabilityKind()) + ",\"paymentMethodCode\":" + quoted(capability.getPaymentMethodCode())
            + ",\"dimensionCode\":" + quoted(capability.getDimensionCode()) + ",\"dimensionValue\":"
            + quoted(capability.getDimensionValue()) + ",\"status\":" + quoted(capability.getStatus()) + "}";
    }

    /** 种子计费事件的期望 JSON（按树比较）。 */
    private static String row(ProductFeeEvent feeEvent) {
        return "{\"productFeeEventId\":\"" + feeEvent.getProductFeeEventId() + "\",\"productCode\":\""
            + feeEvent.getProductCode().getCode() + "\",\"feeEventCode\":\"" + feeEvent.getFeeEventCode()
            + "\",\"termType\":\"" + feeEvent.getTermType() + "\",\"billingFactType\":\"" + feeEvent.getBillingFactType()
            + "\"}";
    }

    /** JSON 字符串或 null。 */
    private static String quoted(Object value) {
        if (value == null) {
            return "null";
        }
        return "\"" + (value instanceof CodeEnum code ? code.getCode() : Objects.toString(value)) + "\"";
    }

    /** 列表中某代码字段。 */
    private static List<String> codes(JsonNode rows, String field) {
        return rows.valueStream().map(row -> row.path(field).asString()).toList();
    }
}
