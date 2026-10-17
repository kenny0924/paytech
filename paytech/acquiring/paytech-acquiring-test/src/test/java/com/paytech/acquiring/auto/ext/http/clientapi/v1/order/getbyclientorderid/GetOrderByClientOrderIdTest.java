package com.paytech.commerce.auto.ext.http.clientapi.v1.order.getbyclientorderid;

import static com.paytech.commerce.test.builder.order.GetOrderDataBuilder.NOW;
import static com.paytech.commerce.test.builder.order.GetOrderDataBuilder.decimal;
import static com.paytech.commerce.test.checker.order.GetOrderResponseChecker.assertAmount;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.commerce.client.api.v1.enums.CommerceCheckoutErrorCode;
import com.paytech.commerce.client.api.v1.enums.OrderCreationMode;
import com.paytech.commerce.client.api.v1.order.ClientOrderApi;
import com.paytech.commerce.domain.enums.CheckoutSessionStatus;
import com.paytech.commerce.domain.enums.FundsAvailabilityStatus;
import com.paytech.commerce.domain.enums.OrderPaymentStatus;
import com.paytech.commerce.domain.enums.OrderStatus;
import com.paytech.commerce.test.base.MockTestBase;
import com.paytech.commerce.test.builder.order.GetOrderDataBuilder.Fixture;
import com.paytech.commerce.test.checker.order.GetOrderResponseChecker;
import com.paytech.commerce.test.checker.order.OrderQueryChecker;
import com.paytech.commerce.test.mocker.local.OrderQueryDataMocker;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;

/**
 * 按 Client 业务订单号查询：精确定位、归属隔离、Query 绑定及本地只读视图。
 *
 * @see ClientOrderApi#getOrderByClientOrderId(long, long, String)
 */
class GetOrderByClientOrderIdTest extends MockTestBase {
    private static final String REQUEST_ID = "Commerce.GetOrderByClientOrderId.Test";

    @Autowired
    private OrderQueryChecker orderChecker;

    @Autowired
    private OrderQueryDataMocker orderData;

    private Fixture fixture;

    /** 每例独立构造已初始化的 Checkout，具体数据由用例插入，不配置外部成功响应。 */
    @BeforeEach
    void setUpClassMocks() {
        fixture = orderData.template().ready().build();
    }

    /** 返回单个订单和原 Checkout 坐标，尚未发起支付时不补建 Intent。 */
    @Test
    void test_readyCheckout() {
        orderData.mock(fixture);

        var body = invoke().assertSuccess().assertItems().getResponseBody();

        assertAll(() -> assertEquals(fixture.getOrder().getOrderNo(), body.getOrderNo()),
            () -> assertEquals("query-order", body.getClientOrderId()),
            () -> assertEquals(OrderCreationMode.HOSTED_CHECKOUT.getCode(), body.getCreationMode()),
            () -> assertEquals(OrderStatus.OPEN.getCode(), body.getOrderStatus()),
            () -> assertEquals("READY", body.getPreparationStatus()),
            () -> assertEquals("FINAL", body.getAmountStatus()),
            () -> assertEquals(OrderPaymentStatus.NOT_STARTED.getCode(), body.getPaymentStatus()),
            () -> assertNull(body.getLastPaymentAttempt()), () -> assertNull(body.getPaidAt()),
            () -> assertEquals("USD", body.getCurrency()), () -> assertEquals("US", body.getConsumerCountryCode()),
            () -> assertEquals(NOW.minusSeconds(60), body.getCreatedAt()), () -> assertEquals(NOW, body.getUpdatedAt()),
            () -> assertEquals(NOW.plusSeconds(1800), body.getExpiresAt()));
        var checkout = body.getCheckout();
        assertNotNull(checkout);
        assertAll(() -> assertEquals(CheckoutSessionStatus.OPEN.getCode(), checkout.getStatus()),
            () -> assertEquals("https://checkout.local.test/c/" + fixture.getSession().getPublicToken(),
                checkout.getUrl()),
            () -> assertEquals(NOW.plusSeconds(1800), checkout.getExpiresAt()),
            () -> assertAmount("50.00", body.getAmounts().getTotalAmount()),
            () -> assertAmount("0.00", body.getAmounts().getPaidAmount()),
            () -> assertEquals(FundsAvailabilityStatus.NOT_APPLICABLE.getCode(),
                body.getFundsAvailability().getStatus()));
    }

    /** 支付、退款预占及已退款事实一并返回，不能只查询订单主表。 */
    @Test
    void test_paidOrderWithPartialRefunds() {
        fixture = orderData.template().paid().build();
        fixture.getRefund().setReservedAmount(decimal("7.25"));
        fixture.getRefund().setRefundedAmount(decimal("12.50"));
        orderData.mock(fixture);

        var body = invoke().assertSuccess().getResponseBody();

        assertAll(() -> assertEquals(fixture.getOrder().getOrderNo(), body.getOrderNo()),
            () -> assertEquals(OrderStatus.PAID.getCode(), body.getOrderStatus()),
            () -> assertEquals(OrderPaymentStatus.SUCCEEDED.getCode(), body.getPaymentStatus()),
            () -> assertEquals(CheckoutSessionStatus.COMPLETED.getCode(), body.getCheckout().getStatus()),
            () -> assertEquals(NOW, body.getPaidAt()), () -> assertAmount("50.00", body.getAmounts().getPaidAmount()),
            () -> assertAmount("7.25", body.getRefundSummary().getProcessingAmount()),
            () -> assertAmount("12.50", body.getRefundSummary().getSucceededAmount()),
            () -> assertAmount("30.25", body.getRefundSummary().getRefundableAmount()),
            () -> assertEquals(FundsAvailabilityStatus.PENDING.getCode(), body.getFundsAvailability().getStatus()));
    }

    /** Direct Pay 没有 Session 也能按业务订单号定位，不生成 Checkout 链接。 */
    @Test
    void test_directPayWithoutSession() {
        fixture = orderData.template().directPay().ready().build();
        orderData.mock(fixture);

        var body = invoke().assertSuccess().getResponseBody();

        assertAll(() -> assertEquals(fixture.getOrder().getOrderNo(), body.getOrderNo()),
            () -> assertEquals("query-order", body.getClientOrderId()),
            () -> assertEquals(OrderCreationMode.DIRECT_PAY.getCode(), body.getCreationMode()),
            () -> assertNull(body.getCheckout()), () -> assertEquals(OrderStatus.OPEN.getCode(), body.getOrderStatus()),
            () -> assertAmount("50.00", body.getAmounts().getTotalAmount()));
    }

    /** 相同业务编号可以属于两个 Client，各自只能读到本 Client 的订单和投影。 */
    @Test
    void test_sameReferenceInDifferentClients() {
        orderData.mock(fixture);
        var other = orderData.template().clientId(checkoutDataMocker.otherClientId()).paid().build();
        other.setContextCountry("AE");
        orderData.mock(other);

        var ownBody = invoke().assertSuccess().getResponseBody();
        var otherBody = invoke(Long.toString(checkoutDataMocker.otherClientId()), "1", "query-order").assertSuccess()
            .getResponseBody();

        assertAll(() -> assertEquals(fixture.getOrder().getOrderNo(), ownBody.getOrderNo()),
            () -> assertEquals("US", ownBody.getConsumerCountryCode()),
            () -> assertAmount("0.00", ownBody.getAmounts().getPaidAmount()),
            () -> assertEquals(other.getOrder().getOrderNo(), otherBody.getOrderNo()),
            () -> assertEquals("AE", otherBody.getConsumerCountryCode()),
            () -> assertAmount("50.00", otherBody.getAmounts().getPaidAmount()));
    }

    /** 未知编号与其他 Client 已有编号均返回 404，不泄漏订单存在性。 */
    @Test
    void test_unknownAndForeignOrder() {
        orderData.mock(fixture);

        invoke("missing-order").assertProblem(404, CommerceCheckoutErrorCode.ORDER_NOT_FOUND.getCode());
        invoke(Long.toString(checkoutDataMocker.otherClientId()), "1", "query-order").assertProblem(404,
            CommerceCheckoutErrorCode.ORDER_NOT_FOUND.getCode());
    }

    /** 当前 Client 没有订单时返回不存在，不返回空对象或空列表。 */
    @Test
    void test_noOrders() {
        invoke().assertProblem(404, CommerceCheckoutErrorCode.ORDER_NOT_FOUND.getCode());
    }

    /** 编号必须匹配 clientOrderId，不能回退到 orderNo 查询。 */
    @Test
    void test_doesNotFallBackToOrderNo() {
        orderData.mock(fixture);

        invoke(fixture.getOrder().getOrderNo()).assertProblem(404, CommerceCheckoutErrorCode.ORDER_NOT_FOUND.getCode());
    }

    /** 相同 Client 下仅大小写不同的业务编号必须能共存并精确定位。 */
    @Test
    void test_caseSensitiveReference() {
        fixture.getOrder().setClientOrderId("Case-Order");
        orderData.mock(fixture);
        var lowerCase = orderData.template().clientOrderId("case-order").directPay().ready().build();
        orderData.mock(lowerCase);

        assertEquals(fixture.getOrder().getOrderNo(),
            invoke("Case-Order").assertSuccess().getResponseBody().getOrderNo());
        assertEquals(lowerCase.getOrder().getOrderNo(),
            invoke("case-order").assertSuccess().getResponseBody().getOrderNo());
        invoke("CASE-ORDER").assertProblem(404, CommerceCheckoutErrorCode.ORDER_NOT_FOUND.getCode());
    }

    /** Query 编号按原字符串解析一次，保留加号、百分号和各类 URL 保留字符。 */
    @ParameterizedTest
    @ValueSource(strings = {"Order+123", "Order&key=value", "Order?x=1#fragment", "Order%2F123", "Order/123",
        "Order{123}", "Order\"123'", "Order\\123", "!~"})
    void test_encodedReference(String clientOrderId) {
        fixture.getOrder().setClientOrderId(clientOrderId);
        orderData.mock(fixture);

        var body = invoke(clientOrderId).assertSuccess().getResponseBody();

        assertAll(() -> assertEquals(fixture.getOrder().getOrderNo(), body.getOrderNo()),
            () -> assertEquals(clientOrderId, body.getClientOrderId()));
    }

    /** 业务编号支持 1 到 128 字符，不套用公开订单号的 64 字符限制。 */
    @ParameterizedTest
    @ValueSource(ints = {1, 128})
    void test_referenceLengthBoundaries(int length) {
        var clientOrderId = "a".repeat(length);
        fixture.getOrder().setClientOrderId(clientOrderId);
        orderData.mock(fixture);

        var body = invoke().assertSuccess().getResponseBody();

        assertAll(() -> assertEquals(fixture.getOrder().getOrderNo(), body.getOrderNo()),
            () -> assertEquals(clientOrderId, body.getClientOrderId()));
    }

    /** 缺失、空白、非 ASCII、控制字符和超长 Query 从真实 HTTP 进入字段校验。 */
    @ParameterizedTest
    @MethodSource("invalidReferences")
    void test_invalidReference(String clientOrderId) {
        orderData.mock(fixture);

        invoke(clientOrderId).assertProblem(400, CommonErrorCode.INVALID_REQUEST.getCode());
    }

    /** 两个可信身份 Header 均经过绑定和正整数校验，错误请求不能读取或修改订单。 */
    @ParameterizedTest
    @MethodSource("invalidIdentities")
    void test_invalidTrustedIdentity(boolean clientHeader, String value) {
        orderData.mock(fixture);

        invoke(clientHeader ? value : Long.toString(checkoutDataMocker.clientId()), clientHeader ? "1" : value,
            fixture.getOrder().getClientOrderId()).assertProblem(400, CommonErrorCode.INVALID_REQUEST.getCode());
    }

    /** Credential 不参与订单归属，允许同一 Client 的另一合法凭据查询。 */
    @Test
    void test_positiveCredentialBoundary() {
        orderData.mock(fixture);

        var body = invoke(Long.toString(checkoutDataMocker.clientId()), Long.toString(Long.MAX_VALUE), "query-order")
            .assertSuccess().getResponseBody();

        assertEquals(fixture.getOrder().getOrderNo(), body.getOrderNo());
    }

    /** 时间跨过到期边界后重新计算链接可见性，不缓存首次响应或推进订单状态。 */
    @Test
    void test_repeatedReadAtExpiry() {
        orderData.mock(fixture);
        assertNotNull(invoke().assertSuccess().getResponseBody().getCheckout().getUrl());
        adjustableUtcClock.advance(Duration.ofMinutes(30));

        var body = invoke().assertSuccess().getResponseBody();

        assertAll(() -> assertNull(body.getCheckout().getUrl()),
            () -> assertEquals(OrderStatus.OPEN.getCode(), body.getOrderStatus()),
            () -> assertEquals(CheckoutSessionStatus.OPEN.getCode(), body.getCheckout().getStatus()),
            () -> assertEquals(NOW.plusSeconds(1800), body.getExpiresAt()));
    }

    /** 必需的退款投影缺失仍为系统错误，不能返回虚构零额度。 */
    @Test
    void test_missingRequiredProjection() {
        fixture.setRefund(null);
        orderData.mock(fixture);

        invoke().assertInternalError();
    }

    /** 默认查询本例 Client 业务编号，不添加请求幂等 Header。 */
    private GetOrderResponseChecker invoke() {
        return invoke(fixture.getOrder().getClientOrderId());
    }

    /** 保留非法或缺失的 Query 值供入口校验。 */
    private GetOrderResponseChecker invoke(String clientOrderId) {
        return invoke(Long.toString(checkoutDataMocker.clientId()), "1", clientOrderId);
    }

    /** 复用已装配的 HTTP 客户端；通过 URI 变量编码 Query，避免保留字符改变参数含义。 */
    private GetOrderResponseChecker invoke(String clientId, String credentialId, String clientOrderId) {
        var before = orderChecker.snapshot(checkoutDataMocker.clientId(), checkoutDataMocker.otherClientId());
        var result = restTestClient.get().uri(builder -> {
            builder.path("/client-gateway-api/v1/orders");
            if (clientOrderId == null) {
                return builder.build();
            }
            return builder.queryParam("clientOrderId", "{clientOrderId}").build(clientOrderId);
        }).headers(headers -> headers.addAll(TestRequestHeaders.client(REQUEST_ID, clientId, credentialId))).exchange()
            .expectBody().returnResult();
        orderChecker.assertUnchanged(before, checkoutDataMocker.clientId(), checkoutDataMocker.otherClientId());
        preparationMocker.assertNoCalls();
        return new GetOrderResponseChecker(result, objectMapper, REQUEST_ID);
    }

    /** 编号必须非空且仅含可打印非空白 ASCII，不能 trim 后继续查询。 */
    private static Stream<String> invalidReferences() {
        return Stream.of(null, "", " ", " query-order", "query-order ", "query order", "query\torder", "query\norder",
            "query\u007Forder", "订单", "a".repeat(129));
    }

    /** 分别改变每个可信身份，避免另一个非法参数提前掩盖被测约束。 */
    private static Stream<Arguments> invalidIdentities() {
        return Stream.of(true, false).flatMap(client -> Stream.of(null, "", "0", "-1", "invalid", "9223372036854775808")
            .map(value -> Arguments.of(client, value)));
    }
}
