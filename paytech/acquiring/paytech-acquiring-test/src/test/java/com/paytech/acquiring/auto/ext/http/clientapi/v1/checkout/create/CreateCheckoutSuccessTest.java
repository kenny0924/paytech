package com.paytech.acquiring.auto.ext.http.clientapi.v1.checkout.create;

import static com.paytech.acquiring.test.builder.checkout.CreateCheckoutRequestBuilder.item;
import static com.paytech.acquiring.test.builder.checkout.CreateCheckoutRequestBuilder.template;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.paytech.acquiring.api.client.v1.checkout.ClientCheckoutApi;
import com.paytech.acquiring.api.client.v1.checkout.request.CreateCheckoutRequestBody;
import com.paytech.acquiring.test.base.checkout.AbstractCreateCheckoutApiTest;
import com.paytech.acquiring.test.mocker.external.CheckoutRiskMocker;

/**
 * 创建成功、金额计算、合法边界与扩展字段。
 *
 * @see ClientCheckoutApi#createCheckout(long, long, String, CreateCheckoutRequestBody)
 */
class CreateCheckoutSuccessTest extends AbstractCreateCheckoutApiTest {
    /** 第二层 BeforeEach：仅准备本类各场景共用的 CCT 预检 DTO。 */
    @BeforeEach
    void setUpClassMocks() {
        mockDefaultPreparation();
    }

    /** 创建返回当前格式，五类本地事实完整，并且 CCT 调用在事务外、写入之前。 */
    @Test
    void test_createCheckout() {
        preparationMocker.observe(call -> checkoutChecker.assertAbsent(clientId(), CLIENT_ORDER_ID));
        var response = invoke(template().build());
        response.assertCreated(CLIENT_ORDER_ID, NOW.plusSeconds(1800), "44.90", "2.00", "42.90");
        checkoutChecker.assertCreated(clientId(), CLIENT_ORDER_ID, response.orderNo(), NOW, NOW.plusSeconds(1800));
        checkoutChecker.assertCheckoutUrl(clientId(), CLIENT_ORDER_ID, response.checkoutUrl());
        checkoutChecker.assertOrderCount(clientId(), otherClientId(), 1);
        checkoutChecker.assertRiskAssessment(clientId(), CLIENT_ORDER_ID, CheckoutRiskMocker.DEFAULT_EVALUATION_ID);
        preparationMocker.assertCalls(clientId(), 1);
        riskMocker.assertCalls(1);
        assertEquals(List.of(false), preparationMocker.transactionStates());
    }

    /** 显式期限的两个精确边界均允许，不能受数据库时间精度影响。 */
    @ParameterizedTest
    @ValueSource(longs = {300, 86400})
    void test_explicitExpiryBoundary(long seconds) {
        Instant expiry = NOW.plusSeconds(seconds);
        var response = invoke(template().expiresAt(expiry).build());
        response.assertCreated(CLIENT_ORDER_ID, expiry, "44.90", "2.00", "42.90");
        checkoutChecker.assertCreated(clientId(), CLIENT_ORDER_ID, response.orderNo(), NOW, expiry);
        preparationMocker.assertCalls(clientId(), 1);
    }

    /** 小数数量和整行优惠精确计算，不乘两次优惠，不隐式舍入。 */
    @Test
    void test_fractionalQuantityAndWholeLineDiscount() {
        var request = template().items(List.of(item("fraction", "Fractional item", "0.5", "10", "1"))).build();
        invoke(request).assertCreated(CLIENT_ORDER_ID, NOW.plusSeconds(1800), "5.00", "1.00", "4.00");
        checkoutChecker.assertAmounts(clientId(), CLIENT_ORDER_ID, "5.00", "1.00", "4.00", 1);
        preparationMocker.assertCalls(clientId(), 1);
    }

    /** 可接受的商品数量上限应完整落库。 */
    @Test
    void test_oneHundredItems() {
        var items = IntStream.range(0, 100).mapToObj(index -> item("line-" + index, "Item", "1", "1", null)).toList();
        invoke(template().items(items).build()).assertCreated(CLIENT_ORDER_ID, NOW.plusSeconds(1800), "100.00", "0.00",
            "100.00");
        checkoutChecker.assertAmounts(clientId(), CLIENT_ORDER_ID, "100.00", "0", "100.00", 100);
        preparationMocker.assertCalls(clientId(), 1);
    }

    /** 正常请求中的未知扩展字段不改变已知业务字段。 */
    @Test
    void test_unknownFieldsAreIgnored() {
        var json = (tools.jackson.databind.node.ObjectNode)objectMapper.valueToTree(template().build());
        json.put("futureField", "ignored");
        ((tools.jackson.databind.node.ObjectNode)json.path("items").get(0)).put("futureItemField", true);
        var result =
            invoke(json.toString()).assertCreated(CLIENT_ORDER_ID, NOW.plusSeconds(1800), "44.90", "2.00", "42.90");
        checkoutChecker.assertCreated(clientId(), CLIENT_ORDER_ID, result.orderNo(), NOW, NOW.plusSeconds(1800));
        preparationMocker.assertCalls(clientId(), 1);
    }
}
