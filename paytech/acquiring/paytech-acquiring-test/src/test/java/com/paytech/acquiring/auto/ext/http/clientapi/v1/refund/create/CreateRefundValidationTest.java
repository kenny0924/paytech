package com.paytech.acquiring.auto.ext.http.clientapi.v1.refund.create;

import static com.paytech.acquiring.test.builder.order.GetOrderDataBuilder.decimal;

import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import com.paytech.acquiring.api.client.v1.refund.ClientRefundApi;
import com.paytech.acquiring.api.client.v1.refund.request.CreateRefundRequestBody;
import com.paytech.acquiring.domain.enums.OrderStatus;
import com.paytech.acquiring.domain.enums.PaymentIntentStatus;
import com.paytech.acquiring.test.base.refund.AbstractCreateRefundApiTest;
import com.paytech.acquiring.test.builder.refund.CreateRefundRequestBuilder;

/**
 * 请求边界、Client 归属、可退额度与缺失/冲突的本地权威事实；失败必须零写入。
 *
 * @see ClientRefundApi#createRefund(long, long, String, String, CreateRefundRequestBody)
 */
class CreateRefundValidationTest extends AbstractCreateRefundApiTest {
    @BeforeEach
    void setUpClassMocks() {
        fixture = orderData.template().paid().build();
    }

    /** 所有非法字段都通过正式 HTTP 绑定校验，不直接调用内部验证方法。 */
    @ParameterizedTest
    @MethodSource("invalidRequests")
    void test_invalidRequest(CreateRefundRequestBody request) {
        orderData.mock(fixture);
        var before = refundChecker.snapshot(fixture.getOrder().getOrderId());
        invoke(request).assertProblem(422, "VALIDATION_ERROR");
        refundChecker.assertRejected(fixture.getOrder().getOrderId(), before);
    }

    private static Stream<Arguments> invalidRequests() {
        var ids = Stream.of(null, "", " ", "with space", "退款", "r".repeat(129))
            .map(v -> CreateRefundRequestBuilder.template().clientRefundId(v).build());
        var amounts = Stream.of(null, "", "0", "0.00", "-1", "+1", "01", ".1", "1.", "1e1", " 1",
            "0.000000001", "10000000000000000")
            .map(v -> CreateRefundRequestBuilder.template().amount(v).build());
        var reasons = Stream.of(null, "", "other", "UNKNOWN")
            .map(v -> CreateRefundRequestBuilder.template().reasonCode(v).build());
        return Stream.of(ids, amounts, reasons,
            Stream.of(CreateRefundRequestBuilder.template().reasonSummary("x".repeat(513)).build()))
            .flatMap(s -> s).map(Arguments::of);
    }

    /** 显式 null 与字段省略不同；数值 Token 不能偷偷转换成金额字符串。 */
    @ParameterizedTest
    @ValueSource(strings = {"null", "{", "[]",
        "{\"clientRefundId\":\"refund-main\",\"amount\":10,\"reasonCode\":\"OTHER\"}",
        "{\"clientRefundId\":\"refund-main\",\"amount\":\"10\",\"reasonCode\":\"OTHER\",\"reasonSummary\":null}"})
    void test_invalidWireBody(String body) {
        orderData.mock(fixture);
        var before = refundChecker.snapshot(fixture.getOrder().getOrderId());
        invoke(Long.toString(checkoutDataMocker.clientId()), "1", fixture.getOrder().getOrderNo(), body)
            .assertProblem(400, "INVALID_REQUEST");
        refundChecker.assertRejected(fixture.getOrder().getOrderId(), before);
    }

    /** 格式合法但超出币种小数精度，业务返回稳定的 422。 */
    @ParameterizedTest
    @CsvSource({"USD,0.001", "JPY,1.1", "KWD,0.0001"})
    void test_currencyPrecision(String currency, String amount) {
        fixture = orderData.template().paid().currency(currency).build();
        fixture.getPayment().setCurrency(currency);
        orderData.mock(fixture);
        var before = refundChecker.snapshot(fixture.getOrder().getOrderId());
        invoke(CreateRefundRequestBuilder.template().amount(amount).build()).assertProblem(422, "VALIDATION_ERROR");
        refundChecker.assertRejected(fixture.getOrder().getOrderId(), before);
    }

    @ParameterizedTest
    @MethodSource("invalidIdentities")
    void test_invalidIdentity(boolean clientHeader, String value) {
        orderData.mock(fixture);
        var before = refundChecker.snapshot(fixture.getOrder().getOrderId());
        invoke(clientHeader ? value : Long.toString(checkoutDataMocker.clientId()), clientHeader ? "1" : value,
            fixture.getOrder().getOrderNo(), request()).assertProblem(400, "INVALID_REQUEST");
        refundChecker.assertRejected(fixture.getOrder().getOrderId(), before);
    }

    private static Stream<Arguments> invalidIdentities() {
        return Stream.of(true, false).flatMap(client -> Stream.of(null, "0", "-1", "invalid", "9223372036854775808")
            .map(value -> Arguments.of(client, value)));
    }

    /** 未知和跨 Client 均不可暴露退款资格。 */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void test_unknownOrForeignOrder(boolean foreign) {
        orderData.mock(fixture);
        var before = refundChecker.snapshot(fixture.getOrder().getOrderId());
        invoke(Long.toString(foreign ? checkoutDataMocker.otherClientId() : checkoutDataMocker.clientId()), "1",
            foreign ? fixture.getOrder().getOrderNo() : "ord_missing", request())
            .assertProblem(404, "ORDER_NOT_FOUND");
        refundChecker.assertRejected(fixture.getOrder().getOrderId(), before);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"CREATED", "OPEN", "CANCELLED", "EXPIRED"})
    void test_unpaidOrder(OrderStatus status) {
        fixture.getOrder().setOrderStatus(status);
        assertBusinessRejection(422, "VALIDATION_ERROR");
    }

    @Test
    void test_paidStatusWithoutPaidAt() {
        fixture.getOrder().setPaidAt(null);
        assertBusinessRejection(422, "VALIDATION_ERROR");
    }

    @ParameterizedTest
    @CsvSource({"50.01,0,0", "10.01,15,25", "0.01,0,50"})
    void test_exceedsRemainingAmount(String amount, String reserved, String refunded) {
        fixture.getRefund().setReservedAmount(decimal(reserved));
        fixture.getRefund().setRefundedAmount(decimal(refunded));
        orderData.mock(fixture);
        var before = refundChecker.snapshot(fixture.getOrder().getOrderId());
        invoke(CreateRefundRequestBuilder.template().amount(amount).build())
            .assertProblem(422, "REFUND_AMOUNT_EXCEEDS_REFUNDABLE");
        refundChecker.assertRejected(fixture.getOrder().getOrderId(), before);
    }

    /** 有状态但没有一致的支付/额度事实时，必须报系统错误而非接受或自行补数据。 */
    @ParameterizedTest
    @ValueSource(strings = {"missing-position", "missing-payment", "position-currency", "negative-reserved",
        "negative-refunded", "over-reserved", "zero-version", "fractional-position", "payment-currency",
        "unsucceeded-payment", "missing-attempt", "captured-mismatch", "base-mismatch", "missing-total"})
    void test_inconsistentPersistedFacts(String scenario) {
        switch (scenario) {
            case "missing-position" -> fixture.setRefund(null);
            case "missing-payment" -> fixture.setPayment(null);
            case "position-currency" -> fixture.getRefund().setCurrency("AED");
            case "negative-reserved" -> fixture.getRefund().setReservedAmount(decimal("-1"));
            case "negative-refunded" -> fixture.getRefund().setRefundedAmount(decimal("-1"));
            case "over-reserved" -> fixture.getRefund().setReservedAmount(decimal("51"));
            case "zero-version" -> fixture.getRefund().setVersion(0);
            case "fractional-position" -> fixture.getRefund().setReservedAmount(decimal("0.001"));
            case "payment-currency" -> fixture.getPayment().setCurrency("AED");
            case "unsucceeded-payment" -> fixture.getPayment().setIntentStatus(PaymentIntentStatus.PROCESSING);
            case "missing-attempt" -> fixture.getPayment().setSuccessfulAttemptId(null);
            case "captured-mismatch" -> fixture.getPayment().setCapturedAmount(decimal("49"));
            case "base-mismatch" -> fixture.getRefund().setRefundableBaseAmount(decimal("49"));
            case "missing-total" -> fixture.getOrder().setTotalAmount(null);
            default -> throw new IllegalArgumentException(scenario);
        }
        assertBusinessRejection(500, "INTERNAL_ERROR");
    }

    private void assertBusinessRejection(int status, String code) {
        orderData.mock(fixture);
        var before = refundChecker.snapshot(fixture.getOrder().getOrderId());
        invoke(request()).assertProblem(status, code);
        refundChecker.assertRejected(fixture.getOrder().getOrderId(), before);
    }
}
