package com.paytech.commerce.auto.ext.http.clientapi.v1.order.get;

import static com.paytech.commerce.test.builder.order.GetOrderDataBuilder.NOW;
import static com.paytech.commerce.test.builder.order.GetOrderDataBuilder.decimal;
import static com.paytech.commerce.test.checker.order.GetOrderResponseChecker.assertAmount;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.paytech.commerce.client.api.v1.order.ClientOrderApi;
import com.paytech.commerce.domain.enums.FundsAvailabilityStatus;
import com.paytech.commerce.domain.enums.PaymentAttemptStatus;
import com.paytech.commerce.domain.enums.PaymentIntentStatus;
import com.paytech.commerce.test.base.order.AbstractGetOrderApiTest;
import com.paytech.commerce.test.builder.order.GetOrderDataBuilder.Payment;

/**
 * 本地支付、退款、资金可用投影的公开映射；GET 不推进这些事实。
 *
 * @see ClientOrderApi#getOrder(long, long, String)
 */
class GetOrderProjectionTest extends AbstractGetOrderApiTest {
    /** 已准备但尚未成功支付的订单，用例分别指定投影事实。 */
    @BeforeEach
    void setUpClassMocks() {
        fixture = orderData.template().ready().build();
    }

    /** 没有未决尝试时按 Intent 映射；REQUIRES_PAYMENT 保留最近失败尝试的结果。 */
    @ParameterizedTest
    @CsvSource({"REQUIRES_PAYMENT,,NOT_STARTED", "PROCESSING,,PROCESSING", "REQUIRES_ACTION,,PROCESSING",
        "SUCCEEDED,SUCCEEDED,SUCCEEDED", "FAILED,FAILED,FAILED", "CANCELLED,,CANCELLED", "EXPIRED,,EXPIRED",
        "REQUIRES_PAYMENT,FAILED,FAILED", "REQUIRES_PAYMENT,RISK_REJECTED,FAILED",
        "REQUIRES_PAYMENT,RISK_FAILED,FAILED", "REQUIRES_PAYMENT,LIMIT_REJECTED,FAILED",
        "REQUIRES_PAYMENT,SUCCESS_CONFLICT,PROCESSING", "REQUIRES_PAYMENT,SUCCEEDED,PROCESSING"})
    void test_paymentStatus(String intent, String attempt, String expected) {
        fixture.setPayment(Payment.builder().intentStatus(PaymentIntentStatus.valueOf(intent))
            .lastAttemptId(attempt == null ? null : fixture.getOrder().getOrderId())
            .lastAttemptStatus(attempt == null ? null : PaymentAttemptStatus.valueOf(attempt)).build());
        orderData.mock(fixture);

        var response = invoke().assertSuccess().getResponseBody();
        assertEquals(expected, response.getPaymentStatus());
        if (attempt == null) {
            assertNull(response.getLastPaymentAttempt());
        } else {
            assertEquals(attempt, response.getLastPaymentAttempt().getStatus());
        }
    }

    /** Intent 终态事件先到时，未决 Attempt 仍表示处理中，UNKNOWN 不能伪装为失败。 */
    @ParameterizedTest
    @ValueSource(strings = {"CREATED", "RISK_CHECKING", "ROUTING", "PROCESSING", "REQUIRES_ACTION", "UNKNOWN"})
    void test_unresolvedAttemptTakesPrecedence(String attempt) {
        fixture.setPayment(
            Payment.builder().intentStatus(PaymentIntentStatus.CANCELLED).lastAttemptId(fixture.getOrder().getOrderId())
                .lastAttemptStatus(PaymentAttemptStatus.valueOf(attempt)).build());
        orderData.mock(fixture);
        var response = invoke().assertSuccess().getResponseBody();
        assertAll(() -> assertEquals("PROCESSING", response.getPaymentStatus()),
            () -> assertEquals(attempt, response.getLastPaymentAttempt().getStatus()),
            () -> assertAmount("0.00", response.getAmounts().getPaidAmount()));
    }

    /** 计划日期已经过去也不能推算成熟；只有已保存的最终投影可以公开可用金额。 */
    @ParameterizedTest
    @CsvSource({"NOT_APPLICABLE,", "PENDING,", "AVAILABLE,47.25", "FULLY_OFFSET,0.00"})
    void test_fundsAvailability(String status, String amount) {
        fixture = orderData.template().paid().build();
        var scheduledAt = NOW.minusSeconds(30);
        var availableAt = NOW.minusSeconds(10);
        fixture.setFunds(funds().fundsAvailabilityStatus(FundsAvailabilityStatus.valueOf(status))
            .scheduledAt(scheduledAt).availableAmount(amount == null ? null : decimal(amount))
            .availableAt(amount == null ? null : availableAt).asOf(NOW.minusSeconds(5)).build());
        orderData.mock(fixture);

        var funds = invoke().assertSuccess().getResponseBody().getFundsAvailability();
        assertAll(() -> assertEquals(status, funds.getStatus()), () -> assertEquals(scheduledAt, funds.getScheduledAt()),
            () -> assertEquals(NOW.minusSeconds(5), funds.getAsOf()));
        if (amount == null) {
            assertAll(() -> assertNull(funds.getAvailableAmount()), () -> assertNull(funds.getAvailableAt()));
        } else {
            assertAll(() -> assertAmount(amount, funds.getAvailableAmount()),
                () -> assertEquals(availableAt, funds.getAvailableAt()));
        }
    }

    /** 完全预占和全部退款都准确返回零可退额，仍保留两者不同的业务含义。 */
    @ParameterizedTest
    @CsvSource({"50.00,0.00", "0.00,50.00"})
    void test_refundBalanceAtZero(String reserved, String refunded) {
        fixture = orderData.template().paid().build();
        fixture.getRefund().setReservedAmount(decimal(reserved));
        fixture.getRefund().setRefundedAmount(decimal(refunded));
        orderData.mock(fixture);
        var refund = invoke().assertSuccess().getResponseBody().getRefundSummary();
        assertAll(() -> assertAmount(reserved, refund.getProcessingAmount()),
            () -> assertAmount(refunded, refund.getSucceededAmount()), () -> assertAmount("0.00", refund.getRefundableAmount()));
    }
}
