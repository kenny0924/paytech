package com.paytech.acquiring.auto.ext.http.clientapi.v1.refund.create;

import static org.junit.jupiter.api.Assertions.*;
import static com.paytech.acquiring.test.builder.order.GetOrderDataBuilder.decimal;

import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import com.paytech.acquiring.api.client.v1.refund.ClientRefundApi;
import com.paytech.acquiring.api.client.v1.refund.request.CreateRefundRequestBody;
import com.paytech.acquiring.domain.enums.RefundStatus;
import com.paytech.acquiring.test.base.refund.AbstractCreateRefundApiTest;
import com.paytech.acquiring.test.builder.refund.CreateRefundRequestBuilder;
import com.paytech.acquiring.test.checker.refund.CreateRefundResponseChecker;

/**
 * 创建受理、业务防重与真实并发请求；已支付事实由测试直接入库。
 *
 * @see ClientRefundApi#createRefund(long, long, String, String, CreateRefundRequestBody)
 */
class CreateRefundTest extends AbstractCreateRefundApiTest {
    /** 每例新建对象，场景按需调整后再插入。 */
    @BeforeEach
    void setUpClassMocks() {
        fixture = orderData.template().paid().build();
    }

    /** Checkout 与 Direct Pay 均允许退款，后者不依赖 Session。 */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void test_acceptRefundAndDispatchAfterCommit(boolean directPay) {
        if (directPay) {
            fixture = orderData.template().paid().directPay().build();
        }
        orderData.mock(fixture);
        var request = request();
        var response = invoke(request).assertAccepted(fixture.getOrder().getOrderNo(), "refund-main", "10.00",
            "USD", adjustableUtcClock.instant()).getResponseBody();

        refundChecker.assertAccepted(fixture, request, response);
        refundChecker.assertPosition(fixture.getOrder().getOrderId(), "10", "0", 10);
    }

    /** 币种精度、最小金额和全额退款输出主单位字符串。 */
    @ParameterizedTest
    @CsvSource({"USD,0.01,0.01", "USD,50,50.00", "JPY,1,1", "KWD,0.001,0.001"})
    void test_currencyAndAmountBoundaries(String currency, String amount, String formatted) {
        fixture = orderData.template().paid().currency(currency).build();
        fixture.getPayment().setCurrency(currency);
        orderData.mock(fixture);
        var request = CreateRefundRequestBuilder.template().amount(amount).build();
        var response = invoke(request).assertAccepted(fixture.getOrder().getOrderNo(), "refund-main", formatted,
            currency, adjustableUtcClock.instant()).getResponseBody();
        refundChecker.assertAccepted(fixture, request, response);
        refundChecker.assertPosition(fixture.getOrder().getOrderId(), amount, "0", 10);
    }

    /** 原支付已经成功，即使 Checkout 期限已过，仍然可以申请退款。 */
    @Test
    void test_refundAfterCheckoutExpiry() {
        fixture.getOrder().setExpiresAt(adjustableUtcClock.instant().minusSeconds(1));
        orderData.mock(fixture);
        var response = invoke(request()).assertAccepted(fixture.getOrder().getOrderNo(), "refund-main", "10.00",
            "USD", adjustableUtcClock.instant()).getResponseBody();
        refundChecker.assertAccepted(fixture, request(), response);
    }

    /** 受控原因与说明原样存储，不丢失可选空字符串和长度边界。 */
    @ParameterizedTest
    @CsvSource(value = {"REQUESTED_BY_CUSTOMER|none", "DUPLICATE|empty", "FRAUDULENT|text", "OTHER|maximum"}, delimiter = '|')
    void test_reasonsAndReferenceBoundary(String code, String summary) {
        orderData.mock(fixture);
        String value = switch (summary) {
            case "none" -> null;
            case "empty" -> "";
            case "maximum" -> "x".repeat(512);
            default -> "Consumer requested cancellation";
        };
        var request = CreateRefundRequestBuilder.template().clientRefundId("R".repeat(128))
            .reasonCode(code).reasonSummary(value).build();
        var response = invoke(request).assertAccepted(fixture.getOrder().getOrderNo(), request.getClientRefundId(),
            "10.00", "USD", adjustableUtcClock.instant()).getResponseBody();
        refundChecker.assertAccepted(fixture, request, response);
    }

    /** 剩余额度需同时扣减在途预占和已成功退款。 */
    @Test
    void test_exactRemainingBalance() {
        fixture.getRefund().setReservedAmount(decimal("15"));
        fixture.getRefund().setRefundedAmount(decimal("25"));
        orderData.mock(fixture);
        var response = invoke(request()).assertAccepted(fixture.getOrder().getOrderNo(), "refund-main", "10.00",
            "USD", adjustableUtcClock.instant()).getResponseBody();
        refundChecker.assertAccepted(fixture, request(), response);
        refundChecker.assertPosition(fixture.getOrder().getOrderId(), "25", "25", 10);
    }

    /** 原任务失败后，Client 以新转发 Key 重提只按 clientRefundId 拿回原回执，不再占额、不刷新时间或重复派发。 */
    @Test
    void test_duplicateReturnsOriginalReceiptWithoutResubmission() {
        orderData.mock(fixture);
        var original = invoke(request()).assertAccepted(fixture.getOrder().getOrderNo(), "refund-main", "10.00",
            "USD", adjustableUtcClock.instant()).getResponseBody();
        refundChecker.assertAccepted(fixture, request(), original);
        var task = refundChecker.awaitRetry(original.getRefundRequestNo());
        adjustableUtcClock.advance(java.time.Duration.ofSeconds(5));
        var equivalent = CreateRefundRequestBuilder.template().amount("10.000").build();

        invoke(Long.toString(fixture.getOrder().getClientId()), "1", fixture.getOrder().getOrderNo(), "new-key",
            equivalent).assertSameReceipt(original);

        var after = refundChecker.awaitRetry(original.getRefundRequestNo());
        assertAll(() -> assertEquals(task.getId(), after.getId()),
            () -> assertEquals(task.getExecuteCount(), after.getExecuteCount()),
            () -> assertEquals(task.getLockVersion(), after.getLockVersion()),
            () -> assertEquals(task.getNextExecuteAt(), after.getNextExecuteAt()),
            () -> assertEquals(original.getCreatedAt(), refundChecker.onlyRefund(fixture.getOrder().getOrderId()).getUpdateTime()));
        refundChecker.assertPosition(fixture.getOrder().getOrderId(), "10", "0", 10);
    }

    /** 任何原退款状态都返回不可变回执，不重新检查已经用尽的额度或加载支付投影。 */
    @ParameterizedTest
    @EnumSource(RefundStatus.class)
    void test_existingRefundDoesNotRequireCurrentPaymentFacts(RefundStatus status) {
        fixture.setPayment(null);
        fixture.setRefund(null);
        orderData.mock(fixture);
        var existing = refundData.refund(fixture, request());
        existing.setRefundStatus(status);
        existing.setCreateTime(adjustableUtcClock.instant().minusSeconds(30));
        if (status.terminal()) {
            existing.setCompletedAt(adjustableUtcClock.instant());
        }
        if (status == RefundStatus.SUCCEEDED) {
            existing.setChannelRefundId(existing.getRefundRequestId());
        }
        refundData.mock(existing);
        var before = refundChecker.snapshot(fixture.getOrder().getOrderId());

        var response = invoke(request()).assertAccepted(fixture.getOrder().getOrderNo(), "refund-main", "10.00",
            "USD", existing.getCreateTime()).getResponseBody();

        assertEquals(existing.getRefundRequestNo(), response.getRefundRequestNo());
        var actual = refundChecker.onlyRefund(fixture.getOrder().getOrderId());
        assertAll(() -> assertEquals(status, actual.getRefundStatus()),
            () -> assertEquals(1, actual.getVersion()), () -> assertEquals(existing.getUpdateTime(), actual.getUpdateTime()),
            () -> assertEquals(before, refundChecker.snapshot(fixture.getOrder().getOrderId())));
        refundChecker.assertNoTask(existing.getRefundRequestNo());
    }

    /** 业务退款号在一个 Client 内唯一，修改金额、原因或说明不得创建新退款。 */
    @ParameterizedTest
    @ValueSource(strings = {"amount", "reason", "summary", "empty-summary", "order"})
    void test_sameBusinessIdWithDifferentContentConflicts(String difference) {
        orderData.mock(fixture);
        var existing = refundData.refund(fixture, request());
        refundData.mock(existing);
        var builder = CreateRefundRequestBuilder.template();
        switch (difference) {
            case "amount" -> builder.amount("11");
            case "reason" -> builder.reasonCode("OTHER");
            case "summary" -> builder.reasonSummary("Changed");
            case "empty-summary" -> builder.reasonSummary("");
            case "order" -> {
                fixture = orderData.template().paid().clientOrderId("second-order").build();
                orderData.mock(fixture);
            }
            default -> throw new IllegalArgumentException(difference);
        }
        var before = refundChecker.snapshot(fixture.getOrder().getOrderId());
        invoke(builder.build()).assertProblem(409, "CLIENT_REFUND_ID_CONFLICT");
        assertEquals(before, refundChecker.snapshot(fixture.getOrder().getOrderId()));
        assertEquals(1, refundChecker.onlyRefund(existing.getOrderId()).getVersion());
        refundChecker.assertNoTask(existing.getRefundRequestNo());
    }

    /** 不同 Client 可以使用同一业务退款号，各自预占自己的订单。 */
    @Test
    void test_businessIdIsScopedToClient() {
        orderData.mock(fixture);
        var first = invoke(request()).getResponseBody();
        refundChecker.assertAccepted(fixture, request(), first);
        fixture = orderData.template().paid().clientId(checkoutDataMocker.otherClientId()).build();
        orderData.mock(fixture);
        var second = invoke(request()).assertAccepted(fixture.getOrder().getOrderNo(), "refund-main", "10.00",
            "USD", adjustableUtcClock.instant()).getResponseBody();
        refundChecker.assertAccepted(fixture, request(), second);
        assertNotEquals(first.getRefundRequestNo(), second.getRefundRequestNo());
    }

    /** 相同 Gateway Key 不会遮蔽第二个业务申请，大小写不同的业务号也是不同申请。 */
    @Test
    void test_gatewayKeyDoesNotReplayBusinessRequests() {
        orderData.mock(fixture);
        var first = invoke(request()).getResponseBody();
        refundChecker.awaitRetry(first.getRefundRequestNo());
        var second = invoke(CreateRefundRequestBuilder.template().clientRefundId("REFUND-MAIN").build())
            .assertAccepted(fixture.getOrder().getOrderNo(), "REFUND-MAIN", "10.00", "USD", adjustableUtcClock.instant())
            .getResponseBody();
        refundChecker.awaitRetry(second.getRefundRequestNo());
        assertNotEquals(first.getRefundRequestNo(), second.getRefundRequestNo());
        refundChecker.assertPosition(fixture.getOrder().getOrderId(), "20", "0", 11);
    }

    /** 两个真实 HTTP 请求争用同一业务号，只能得到一份退款和一次预占。 */
    @Test
    void test_concurrentSameBusinessId() throws Exception {
        orderData.mock(fixture);
        var responses = concurrently(request(), request());
        var first = responses.getFirst().assertAccepted(fixture.getOrder().getOrderNo(), "refund-main", "10.00",
            "USD", adjustableUtcClock.instant()).getResponseBody();
        responses.getLast().assertSameReceipt(first);
        refundChecker.assertAccepted(fixture, request(), first);
        refundChecker.assertPosition(fixture.getOrder().getOrderId(), "10", "0", 10);
    }

    /** 两个不同业务号同时抢剩余可退金额，只能一笔成功；失败不能留下申请和预占。 */
    @Test
    void test_concurrentRefundsCannotOverReserve() throws Exception {
        orderData.mock(fixture);
        var first = CreateRefundRequestBuilder.template().amount("30").build();
        var second = CreateRefundRequestBuilder.template().clientRefundId("refund-second").amount("30").build();
        var responses = concurrently(first, second);
        assertEquals(1, responses.stream().filter(r -> r.status() == 201).count());
        for (var response : responses) {
            if (response.status() == 201) {
                refundChecker.awaitRetry(response.getResponseBody().getRefundRequestNo());
            } else if (response.status() == 409) {
                response.assertProblem(409, "CONCURRENT_REQUEST");
            } else {
                response.assertProblem(422, "REFUND_AMOUNT_EXCEEDS_REFUNDABLE");
            }
        }
        var winner = responses.stream().filter(r -> r.status() == 201).findFirst().orElseThrow().getResponseBody();
        assertEquals(winner.getRefundRequestNo(), refundChecker.onlyRefund(fixture.getOrder().getOrderId()).getRefundRequestNo());
        refundChecker.assertPosition(fixture.getOrder().getOrderId(), "30", "0", 10);
    }

    /** 屏障只协调测试调用时机，不替换生产查询、锁或事务。 */
    private List<CreateRefundResponseChecker> concurrently(CreateRefundRequestBody first, CreateRefundRequestBody second)
        throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);
            var futures = Stream.of(first, second).map(body -> executor.submit(() -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return invoke(body);
            })).toList();
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            return List.of(futures.getFirst().get(15, TimeUnit.SECONDS), futures.getLast().get(15, TimeUnit.SECONDS));
        }
    }
}
