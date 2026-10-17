package com.paytech.acquiring.auto.ext.http.clientapi.v1.order.get;

import static org.junit.jupiter.api.Assertions.*;

import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.paytech.acquiring.api.client.v1.order.ClientOrderApi;
import com.paytech.acquiring.test.base.order.AbstractGetOrderApiTest;

/**
 * 订单标识、HTTP 身份绑定及 Client 归属隔离。
 *
 * @see ClientOrderApi#getOrder(long, long, String)
 */
class GetOrderValidationTest extends AbstractGetOrderApiTest {
    /** 真实存储一个可查订单，验证错误参数不会影响已有事实。 */
    @BeforeEach
    void setUpClassMocks() {
        fixture = orderData.template().build();
        orderData.mock(fixture);
    }

    /** 未知与其他 Client 的订单使用相同错误，不泄漏资源是否存在。 */
    @Test
    void test_unknownAndForeignOrderAreIndistinguishable() {
        invoke("ord_unknown").assertProblem(404, "ORDER_NOT_FOUND");
        invoke(Long.toString(checkoutDataMocker.otherClientId()), "1", fixture.getOrder().getOrderNo())
            .assertProblem(404, "ORDER_NOT_FOUND");
    }

    /** 两个 Client 可以有相同 clientOrderId，不能据此跨 Client 查到对方订单。 */
    @Test
    void test_sameClientOrderIdInDifferentClients() {
        var other = orderData.template().clientId(checkoutDataMocker.otherClientId()).build();
        other.getOrder().setConsumerCountryCode("AE");
        orderData.mock(other);
        var response = invoke().assertSuccess().getResponseBody();
        assertAll(() -> assertEquals(fixture.getOrder().getOrderNo(), response.getOrderNo()),
            () -> assertEquals("US", response.getConsumerCountryCode()));
        invoke(other.getOrder().getOrderNo()).assertProblem(404, "ORDER_NOT_FOUND");
        var otherResponse = invoke(Long.toString(checkoutDataMocker.otherClientId()), "1", other.getOrder().getOrderNo())
            .assertSuccess().getResponseBody();
        assertAll(() -> assertEquals(other.getOrder().getOrderNo(), otherResponse.getOrderNo()),
            () -> assertEquals("AE", otherResponse.getConsumerCountryCode()));
    }

    /** orderNo 大小写敏感，不 trim 或把无法匹配的标识当作其他订单。 */
    @ParameterizedTest
    @ValueSource(strings = {"case", "leading-space"})
    void test_orderNoIsExact(String variation) {
        var orderNo = fixture.getOrder().getOrderNo();
        var requested = "case".equals(variation) ? "ORD_" + orderNo.substring(4) : " " + orderNo;
        invoke(requested).assertProblem(404, "ORDER_NOT_FOUND");
    }

    /** 缺失、非法、非正数和溢出身份在 HTTP 绑定阶段拒绝。 */
    @ParameterizedTest
    @MethodSource("invalidIdentities")
    void test_invalidTrustedIdentity(boolean clientHeader, String value) {
        String client = clientHeader ? value : Long.toString(checkoutDataMocker.clientId());
        String credential = clientHeader ? "1" : value;
        invoke(client, credential, fixture.getOrder().getOrderNo()).assertProblem(400, "INVALID_REQUEST");
    }

    /** 凭据 ID 只按正整数合同绑定，查询范围仍由可信 Client 身份决定。 */
    @Test
    void test_positiveCredentialBoundary() {
        invoke(Long.toString(checkoutDataMocker.clientId()), Long.toString(Long.MAX_VALUE),
            fixture.getOrder().getOrderNo()).assertSuccess();
    }

    /** 合同允许 64 字符标识，无需把历史业务标识强制解析成新 Token。 */
    @Test
    void test_maximumOrderNoLength() {
        var boundary = orderData.template().clientOrderId("length-boundary").orderNo("x".repeat(64)).build();
        orderData.mock(boundary);
        var response = invoke(boundary.getOrder().getOrderNo()).assertSuccess().getResponseBody();
        assertEquals("x".repeat(64), response.getOrderNo());
    }

    /** 超长及全空白路径参数从真实 HTTP 进入校验。 */
    @ParameterizedTest
    @ValueSource(strings = {"too-long", "blank"})
    void test_invalidOrderNo(String scenario) {
        invoke("too-long".equals(scenario) ? "x".repeat(65) : " ").assertProblem(400, "INVALID_REQUEST");
    }

    private static Stream<Arguments> invalidIdentities() {
        return Stream.of(true, false).flatMap(client -> Stream.of(null, "0", "-1", "invalid", "9223372036854775808")
            .map(value -> Arguments.of(client, value)));
    }
}
