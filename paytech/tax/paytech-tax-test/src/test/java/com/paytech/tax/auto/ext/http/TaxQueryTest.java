package com.paytech.tax.auto.ext.http;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.tax.api.TaxCalculationApi;
import com.paytech.tax.api.TaxOrderQueryApi;
import com.paytech.tax.api.enums.ManagedBy;
import com.paytech.tax.api.enums.TaxCalculationStage;
import com.paytech.tax.api.enums.TaxErrorCode;
import com.paytech.tax.core.enums.FilingFrequency;
import com.paytech.tax.test.base.MockTestBase;
import com.paytech.tax.test.builder.CalculateTaxRequestBuilder;
import com.paytech.tax.test.builder.TaxTestConstants;
import com.paytech.tax.test.checker.TaxResponseChecker;

import tools.jackson.databind.JsonNode;

/**
 * 按 ID 读取计算与按订单号读取税务全貌（运营税务查询页的数据源）
 *
 * @see TaxCalculationApi#getTaxCalculation(String)
 * @see TaxOrderQueryApi#getOrderTax(String)
 */
class TaxQueryTest extends MockTestBase {
    /** 测试主体在 SA 与 AE 的注册与准入。 */
    @BeforeEach
    void setUpClassMocks() {
        subjects.registered(1001L, "SA", TaxTestConstants.SEED_POLICY_SA, TaxTestConstants.USD, "Asia/Riyadh",
            FilingFrequency.QUARTERLY);
        subjects.registered(1011L, "AE", TaxTestConstants.SEED_POLICY_AE, TaxTestConstants.USD, "Asia/Dubai",
            FilingFrequency.QUARTERLY);
    }

    /** 不存在的计算 404。 */
    @Test
    void test_calculationNotFound() {
        get("/tax/v1/calculations/123456789").assertProblem(404, TaxErrorCode.TAX_CALCULATION_NOT_FOUND.getCode());
    }

    /** 没有任何计算的订单 404。 */
    @Test
    void test_orderNotFound() {
        get("/tax/v1/orders/ORD-UNKNOWN").assertProblem(404, TaxErrorCode.TAX_ORDER_NOT_FOUND.getCode());
    }

    /** 按 ID 读取与计算响应一致。 */
    @Test
    void test_getCalculationById() {
        TaxResponseChecker created = post(CalculateTaxTest.PATH, CalculateTaxRequestBuilder.template().build())
            .assertSuccess(200);

        get("/tax/v1/calculations/" + created.text("calculationId")).assertSuccess(200)
            .assertText("calculationId", created.text("calculationId")).assertText("taxAmount", "15.00")
            .assertText("sourceObjectNo", TaxTestConstants.ORDER_NO);
    }

    /** 两次报价按准备版本升序返回（暂估 AE、最终 SA），附带引用的注册与地区包；未支付时没有正式交易。 */
    @Test
    void test_orderTaxWithQuoteRevisions() {
        post(CalculateTaxTest.PATH, CalculateTaxRequestBuilder.template().revision(1).stage(TaxCalculationStage.PROVISIONAL)
            .country("AE").build()).assertSuccess(200);
        post(CalculateTaxTest.PATH, CalculateTaxRequestBuilder.template().revision(2).build()).assertSuccess(200);

        JsonNode body = get("/tax/v1/orders/" + TaxTestConstants.ORDER_NO).assertSuccess(200)
            .assertText("orderNo", TaxTestConstants.ORDER_NO).json();

        assertEquals(2, body.path("calculations").size());
        assertEquals(1, body.path("calculations").get(0).path("sourceRevision").asInt());
        assertEquals("5.00", body.path("calculations").get(0).path("taxAmount").asString());
        assertEquals(2, body.path("calculations").get(1).path("sourceRevision").asInt());
        assertEquals("15.00", body.path("calculations").get(1).path("taxAmount").asString());
        assertEquals(0, body.path("transactions").size());
        assertEquals(Set.of("1001", "1011"), values(body.path("registrations"), "registrationId"));
        body.path("registrations").forEach(registration -> assertEquals(ManagedBy.PLATFORM_OPERATIONS.getCode(),
            registration.path("managedBy").asString()));
        assertEquals("Item 1", body.path("calculations").get(1).path("lines").get(0).path("description").asString());
        assertEquals(Set.of("DEMO_SA_SAAS", "DEMO_AE_SAAS"), values(body.path("policies"), "packageCode"));
    }

    /** 数组元素字段值集合。 */
    static Set<String> values(JsonNode array, String field) {
        Set<String> values = new HashSet<>();
        array.forEach(item -> values.add(item.path(field).asString()));
        assertEquals(array.size(), values.size(), field + " 不应重复");
        return values;
    }
}
