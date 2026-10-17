package com.paytech.commerce.auto.ext.http.clientapi.v1.order.get;

import static com.paytech.commerce.test.builder.order.GetOrderDataBuilder.NOW;
import static com.paytech.commerce.test.builder.order.GetOrderDataBuilder.decimal;
import static com.paytech.commerce.test.checker.order.GetOrderResponseChecker.assertAmount;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.paytech.commerce.client.api.v1.order.ClientOrderApi;
import com.paytech.commerce.domain.enums.CheckoutSessionStatus;
import com.paytech.commerce.domain.enums.OrderStatus;
import com.paytech.commerce.test.base.order.AbstractGetOrderApiTest;

/**
 * 订单详情、创建方式、金额、期限与公开字段合同。
 *
 * @see ClientOrderApi#getOrder(long, long, String)
 */
class GetOrderSuccessTest extends AbstractGetOrderApiTest {
    /** 本类基线为待准备订单；用例只声明不同的业务事实。 */
    @BeforeEach
    void setUpClassMocks() {
        fixture = orderData.template().build();
    }

    /** 尚未准备时只返回预估金额，真实商品排序与公开结构完整。 */
    @Test
    void test_pendingCheckout() {
        orderData.mock(fixture);
        var response = invoke().assertSuccess().assertItems().getResponseBody();
        assertAll(() -> assertEquals(fixture.getOrder().getOrderNo(), response.getOrderNo()),
            () -> assertEquals("query-order", response.getClientOrderId()),
            () -> assertEquals("HOSTED_CHECKOUT", response.getCreationMode()),
            () -> assertEquals("CREATED", response.getOrderStatus()),
            () -> assertEquals("PENDING", response.getPreparationStatus()),
            () -> assertEquals("ESTIMATED", response.getAmountStatus()),
            () -> assertEquals("NOT_STARTED", response.getPaymentStatus()),
            () -> assertEquals("USD", response.getCurrency()),
            () -> assertEquals("US", response.getConsumerCountryCode()),
            () -> assertEquals(NOW.plusSeconds(1800), response.getExpiresAt()),
            () -> assertEquals(NOW.minusSeconds(60), response.getCreatedAt()),
            () -> assertEquals(NOW, response.getUpdatedAt()), () -> assertNull(response.getPaidAt()),
            () -> assertNull(response.getLastPaymentAttempt()));
        var amounts = response.getAmounts();
        assertAll(() -> assertAmount("50.00", amounts.getMerchandiseAmount()),
            () -> assertAmount("2.00", amounts.getDiscountAmount()),
            () -> assertAmount("48.00", amounts.getNetAmount()), () -> assertNull(amounts.getTaxAmount()),
            () -> assertNull(amounts.getRoundingAmount()), () -> assertNull(amounts.getTotalAmount()),
            () -> assertAmount("0.00", amounts.getPaidAmount()));
        var checkout = response.getCheckout();
        assertAll(() -> assertEquals("PREPARING", checkout.getStatus()),
            () -> assertEquals("https://checkout.local.test/c/" + fixture.getSession().getPublicToken(),
                checkout.getUrl()),
            () -> assertEquals(NOW.plusSeconds(1800), checkout.getExpiresAt()));
        var refund = response.getRefundSummary();
        assertAll(() -> assertAmount("0.00", refund.getProcessingAmount()),
            () -> assertAmount("0.00", refund.getSucceededAmount()),
            () -> assertAmount("0.00", refund.getRefundableAmount()));
        var funds = response.getFundsAvailability();
        assertAll(() -> assertEquals("NOT_APPLICABLE", funds.getStatus()), () -> assertNull(funds.getAvailableAmount()),
            () -> assertNull(funds.getAvailableAt()), () -> assertNull(funds.getScheduledAt()),
            () -> assertEquals(NOW, funds.getAsOf()), () -> assertEquals(NOW, funds.getUpdatedAt()));
    }

    /** 支付成功后保持原链接，退款可用金额扣除处理中的预占和已成功退款。 */
    @Test
    void test_paidOrderWithPartialRefunds() {
        fixture = orderData.template().paid().build();
        fixture.getRefund().setReservedAmount(decimal("7.25"));
        fixture.getRefund().setRefundedAmount(decimal("12.50"));
        orderData.mock(fixture);
        var response = invoke().assertSuccess().getResponseBody();
        assertAll(() -> assertEquals("PAID", response.getOrderStatus()),
            () -> assertEquals("READY", response.getPreparationStatus()),
            () -> assertEquals("FINAL", response.getAmountStatus()),
            () -> assertEquals("SUCCEEDED", response.getPaymentStatus()),
            () -> assertEquals("SUCCEEDED", response.getLastPaymentAttempt().getStatus()),
            () -> assertEquals(NOW, response.getPaidAt()));
        var amounts = response.getAmounts();
        assertAll(() -> assertAmount("2.00", amounts.getTaxAmount()),
            () -> assertAmount("0.00", amounts.getRoundingAmount()),
            () -> assertAmount("50.00", amounts.getTotalAmount()),
            () -> assertAmount("50.00", amounts.getPaidAmount()));
        var checkout = response.getCheckout();
        assertAll(() -> assertEquals("COMPLETED", checkout.getStatus()),
            () -> assertEquals("https://checkout.local.test/c/" + fixture.getSession().getPublicToken(),
                checkout.getUrl()));
        var refund = response.getRefundSummary();
        assertAll(() -> assertAmount("7.25", refund.getProcessingAmount()),
            () -> assertAmount("12.50", refund.getSucceededAmount()),
            () -> assertAmount("30.25", refund.getRefundableAmount()));
    }

    /** Direct Pay 按实际关联返回 checkout，不能根据 creationMode 擅自生成或隐藏。 */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void test_directPaySessionIsOptional(boolean hasSession) {
        fixture = orderData.template().directPay().session(hasSession).ready().build();
        orderData.mock(fixture);
        var response = invoke().assertSuccess().getResponseBody();
        assertAll(() -> assertEquals("DIRECT_PAY", response.getCreationMode()),
            () -> assertEquals("READY", response.getPreparationStatus()),
            () -> assertAmount("50.00", response.getAmounts().getTotalAmount()));
        if (hasSession) {
            assertEquals("OPEN", response.getCheckout().getStatus());
        } else {
            assertNull(response.getCheckout());
        }
    }

    /** 精确过期边界只隐藏链接，不推进状态、不延长期限；重复 GET 仍保持事实。 */
    @ParameterizedTest
    @ValueSource(longs = {-1, 0, 1})
    void test_expiryBoundary(long remainingMicroseconds) {
        var expiry = NOW.plusNanos(remainingMicroseconds * 1000);
        fixture.getOrder().setExpiresAt(expiry);
        orderData.mock(fixture);
        for (int count = 0; count < 2; count++) {
            var response = invoke().assertSuccess().getResponseBody();
            var checkout = response.getCheckout();
            assertAll(() -> assertEquals("CREATED", response.getOrderStatus()),
                () -> assertEquals("PREPARING", checkout.getStatus()),
                () -> assertEquals(expiry, response.getExpiresAt()),
                () -> assertEquals(expiry, checkout.getExpiresAt()));
            if (remainingMicroseconds > 0) {
                assertEquals("https://checkout.local.test/c/" + fixture.getSession().getPublicToken(),
                    checkout.getUrl());
            } else {
                assertNull(checkout.getUrl());
            }
        }
    }

    /** 提前关闭的订单允许没有最终金额，查询不得因为终态而报错。 */
    @ParameterizedTest
    @CsvSource({"CANCELLED,true", "EXPIRED,true", "CANCELLED,false", "EXPIRED,false", "CREATED,false"})
    void test_closedOrUnpreparedOrders(String status, boolean hosted) {
        fixture = hosted ? orderData.template().build() : orderData.template().directPay().build();
        fixture.getOrder().setOrderStatus(OrderStatus.valueOf(status));
        if (hosted) {
            fixture.getSession().setSessionStatus(CheckoutSessionStatus.valueOf(status));
        }
        orderData.mock(fixture);
        var response = invoke().assertSuccess().getResponseBody();
        assertAll(() -> assertEquals(status, response.getOrderStatus()),
            () -> assertEquals("ESTIMATED", response.getAmountStatus()),
            () -> assertEquals("PENDING", response.getPreparationStatus()),
            () -> assertNull(response.getAmounts().getTotalAmount()));
    }

    /** 冻结上下文优先于基础创建国家；旧订单的基础国家允许为空。 */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void test_frozenConsumerCountry(boolean missingCreationCountry) {
        fixture = orderData.template().ready().build();
        fixture.setContextCountry("AE");
        if (missingCreationCountry) {
            fixture.getOrder().setConsumerCountryCode(null);
        }
        orderData.mock(fixture);
        var response = invoke().assertSuccess().getResponseBody();
        assertEquals("AE", response.getConsumerCountryCode());
    }

    /** 读取已存在的不同币种订单，金额使用各自币种精度，数量不受该精度限制。 */
    @ParameterizedTest
    @CsvSource({"JPY,50,10,0", "BHD,50.000,10.000,0.000", "USD,50.00,10.00,0.00"})
    void test_currencyPrecision(String currency, String total, String unit, String zero) {
        fixture = orderData.template().currency(currency).ready().build();
        orderData.mock(fixture);
        var response = invoke().assertSuccess().getResponseBody();
        var first = response.getItems().getFirst();
        assertAll(() -> assertEquals(currency, response.getCurrency()),
            () -> assertAmount(total, response.getAmounts().getTotalAmount()),
            () -> assertAmount(unit, first.getUnitAmount()),
            () -> assertAmount(zero, response.getAmounts().getPaidAmount()),
            () -> assertEquals("2.5", first.getQuantity()));
    }

    /** 根 updatedAt 必须取所有本地事实最大值，不仅返回订单更新时间。 */
    @ParameterizedTest
    @ValueSource(strings = {"order", "session", "payment", "refund", "funds"})
    void test_latestProjectionTimestamp(String source) {
        fixture = orderData.template().paid().build();
        Instant latest = NOW.plusSeconds(123).plusNanos(456000);
        switch (source) {
            case "order" -> fixture.getOrder().setUpdateTime(latest);
            case "session" -> fixture.getSession().setUpdateTime(latest);
            case "payment" -> fixture.getPayment().setUpdateTime(latest);
            case "refund" -> fixture.getRefund().setUpdateTime(latest);
            case "funds" -> fixture.setFunds(funds().updateTime(latest).build());
            default -> throw new AssertionError(source);
        }
        orderData.mock(fixture);
        var response = invoke().assertSuccess().getResponseBody();
        assertEquals(latest, response.getUpdatedAt());
    }
}
