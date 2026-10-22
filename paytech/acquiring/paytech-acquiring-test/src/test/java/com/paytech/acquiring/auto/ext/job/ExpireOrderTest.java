package com.paytech.acquiring.auto.ext.job;

import static com.paytech.acquiring.test.builder.order.GetOrderDataBuilder.NOW;
import static com.paytech.acquiring.test.mocker.local.OrderExpiryDataMocker.DUE_AT;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.util.UriComponentsBuilder;

import com.paytech.acquiring.api.checkout.v1.enums.CustomerCheckoutErrorCode;
import com.paytech.acquiring.api.client.v1.enums.CommerceCheckoutErrorCode;
import com.paytech.acquiring.core.constant.Locks;
import com.paytech.acquiring.domain.enums.CheckoutSessionStatus;
import com.paytech.acquiring.domain.enums.ClientWebhookEventType;
import com.paytech.acquiring.domain.enums.OrderPaymentStatus;
import com.paytech.acquiring.domain.enums.OrderStatus;
import com.paytech.acquiring.domain.enums.PaymentIntentStatus;
import com.paytech.acquiring.domain.webhook.ClientWebhookEvent;
import com.paytech.acquiring.ext.job.OrderExpiryJobHandler;
import com.paytech.acquiring.integration.payment.PaymentTerminationClient.Status;
import com.paytech.acquiring.integration.payment.PaymentTerminationClient.TerminationReason;
import com.paytech.acquiring.service.processor.order.ExpireOrderProcessor;
import com.paytech.acquiring.service.processor.order.request.ExpireOrderRequest;
import com.paytech.acquiring.service.processor.order.response.ExpireOrderResponse;
import com.paytech.acquiring.service.processor.order.response.ScanExpiredOrdersResponse;
import com.paytech.acquiring.test.base.MockTestBase;
import com.paytech.acquiring.test.builder.customer.SubmitPaymentRequestBuilder;
import com.paytech.acquiring.test.builder.order.GetOrderDataBuilder.Fixture;
import com.paytech.acquiring.test.checker.customer.CheckoutFlowResponseChecker;
import com.paytech.acquiring.test.checker.order.CancelOrderResponseChecker;
import com.paytech.acquiring.test.checker.order.GetOrderResponseChecker;
import com.paytech.acquiring.test.checker.order.OrderExpiryChecker;
import com.paytech.acquiring.test.checker.webhook.ClientWebhookChecker;
import com.paytech.acquiring.test.mocker.external.PaymentTerminationMocker;
import com.paytech.acquiring.test.mocker.external.PaymentTerminationMocker.SourceQuery;
import com.paytech.acquiring.test.mocker.external.PaymentTerminationMocker.Termination;
import com.paytech.acquiring.test.mocker.local.OrderExpiryConfigMocker;
import com.paytech.acquiring.test.mocker.local.OrderExpiryDataMocker;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.http.ApiHeaderNames;
import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.foundation.httpproblem.api.ApiProblemResponse;
import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.xxl.job.core.context.XxlJobContext;

/**
 * 到期订单的过期收尾：无 Intent 本地收尾；有 Intent 按 Payment 终止决定收尾或保持开放；重复扫描、并发与先取消后扫描只写一次。
 * <li>入口为 XXL Handler：runOnce 显式指定分片，XXL 入口方法由测试侧设置或省略 XXL 上下文；批大小、页数与告警阈值来自配置。</li>
 * <li>直接调用收尾 Processor 只用于模拟另一实例的重复处理。</li>
 *
 * @see OrderExpiryJobHandler#runOnce(int, int)
 * @see OrderExpiryJobHandler#scan()
 * @see ExpireOrderProcessor#process(Object)
 */
@ExtendWith(OutputCaptureExtension.class)
class ExpireOrderTest extends MockTestBase {
    private static final String REQUEST_ID = "Commerce.ExpireOrder.Test";
    private static final String PENDING_ALERT = "Order expiry termination still pending beyond";

    @Autowired
    private OrderExpiryJobHandler job;
    @Autowired
    private ExpireOrderProcessor expirer;
    @Autowired
    private OrderExpiryDataMocker data;
    @Autowired
    private OrderExpiryConfigMocker config;
    @Autowired
    private OrderExpiryChecker checker;
    @Autowired
    private PaymentTerminationMocker terminations;
    @Autowired
    private ClientWebhookChecker webhooks;
    @Autowired
    private RedissonClient redisson;

    /** 到期且无 Intent：CREATED 无会话与 OPEN 未提交支付均本地收尾，不调用 Payment；GET 订单为 EXPIRED 且无收银台地址。 */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void test_expireWithoutIntent(boolean open) {
        var fixture = open ? data.mockOpen() : data.mockCreatedWithoutSession();
        long orderId = orderId(fixture);

        assertOnly(scan(), ExpireOrderResponse.EXPIRED);

        checker.assertExpired(orderId, open, NOW);
        checker.assertNoPayment(orderId);
        terminations.assertNoCalls();
        webhooks.awaitSubmissions(eventId(fixture), 1);
        var body = getOrder(fixture).assertStatus(200).getResponseBody();
        assertAll(() -> assertEquals(OrderStatus.EXPIRED.getCode(), body.getOrderStatus()),
            () -> assertEquals(OrderPaymentStatus.NOT_STARTED.getCode(), body.getPaymentStatus()));
        if (open) {
            assertNotNull(body.getCheckout());
            assertEquals(CheckoutSessionStatus.EXPIRED.getCode(), body.getCheckout().getStatus());
            assertNull(body.getCheckout().getUrl());
        } else {
            assertNull(body.getCheckout());
        }
    }

    /** 有投影且 Payment 终止或此前已终止：以 ORDER_EXPIRED 终止投影中的 Intent，订单、会话、投影同时 EXPIRED。 */
    @ParameterizedTest
    @ValueSource(strings = {"TERMINATED", PaymentTerminationMocker.ALREADY_TERMINATED})
    void test_terminatedWithProjection(String decision) {
        var fixture = data.mockWithProjection();
        long orderId = orderId(fixture);
        terminations.mockDecision(orderId, decision);

        assertOnly(scan(), ExpireOrderResponse.EXPIRED);

        checker.assertExpired(orderId, true, NOW);
        checker.assertPaymentStatus(orderId, PaymentIntentStatus.EXPIRED);
        assertEquals(List.of(Termination.of(orderId, clientId(), TerminationReason.ORDER_EXPIRED)),
            terminations.calls());
        assertTrue(terminations.sourceQueries().isEmpty(), "已有投影不按来源查询");
        webhooks.awaitSubmissions(eventId(fixture), 1);
        var body = getOrder(fixture).assertStatus(200).getResponseBody();
        assertAll(() -> assertEquals(OrderStatus.EXPIRED.getCode(), body.getOrderStatus()),
            () -> assertEquals(OrderPaymentStatus.EXPIRED.getCode(), body.getPaymentStatus()));
    }

    /** 无投影但有接受证据：先按来源定位 Intent，再以 ORDER_EXPIRED 终止，不补写投影。 */
    @Test
    void test_acceptedSourceFoundTerminates() {
        var context = data.mockAcceptedWithoutProjection();
        long orderId = context.getOrder().getOrderId();
        terminations.mockSource(orderId, orderId);
        terminations.mockDecision(orderId, Status.TERMINATED);

        assertOnly(scan(), ExpireOrderResponse.EXPIRED);

        checker.assertExpired(orderId, true, NOW);
        checker.assertNoPayment(orderId);
        assertEquals(List.of(SourceQuery.of(orderId, clientId())), terminations.sourceQueries());
        assertEquals(List.of(Termination.of(orderId, clientId(), TerminationReason.ORDER_EXPIRED)),
            terminations.calls());
    }

    /** 无投影但有接受证据、按来源查询 404：Intent 不存在，本地收尾且不调用终止。 */
    @Test
    void test_acceptedSourceNotFoundExpires() {
        var context = data.mockAcceptedWithoutProjection();
        long orderId = context.getOrder().getOrderId();
        terminations.mockSourceNotFound(orderId);

        assertOnly(scan(), ExpireOrderResponse.EXPIRED);

        checker.assertExpired(orderId, true, NOW);
        assertEquals(List.of(SourceQuery.of(orderId, clientId())), terminations.sourceQueries());
        assertTrue(terminations.calls().isEmpty(), "Intent 不存在时不终止");
    }

    /** PENDING：订单保持 OPEN、终止意图已写、收银台提交被拒；Payment 收敛后下一轮完成收尾。 */
    @Test
    void test_pendingKeepsOpenUntilPaymentConverges() {
        var fixture = data.mockWithProjection();
        long orderId = orderId(fixture);
        terminations.mockDecision(orderId, Status.PENDING);

        assertOnly(scan(), ExpireOrderResponse.PENDING);

        checker.assertNotExpired(orderId, OrderStatus.OPEN, true);
        checker.assertSessionStatus(orderId, CheckoutSessionStatus.OPEN);
        checker.assertPaymentStatus(orderId, PaymentIntentStatus.REQUIRES_PAYMENT);
        webhooks.assertNoTask(eventId(fixture));
        // 已到期的收银台在终止意图之前先按到期拒绝新的支付提交。
        submitPayment(fixture).assertProblem(410, CustomerCheckoutErrorCode.CHECKOUT_EXPIRED.getCode());

        terminations.mockDecision(orderId, Status.TERMINATED);
        adjustableUtcClock.advance(Duration.ofMinutes(1));
        assertOnly(scan(), ExpireOrderResponse.EXPIRED);

        checker.assertExpired(orderId, true, NOW.plusSeconds(60));
        assertEquals(NOW, checker.order(orderId).getTerminationRequestedAt(), "终止意图保留首次时刻");
        assertEquals(2, terminations.calls().size());
    }

    /** ALREADY_PAID 与 REVERSAL_REQUIRED：订单保持 OPEN，只写终止意图，不登记 order.expired。 */
    @ParameterizedTest
    @EnumSource(value = Status.class, names = {"ALREADY_PAID", "REVERSAL_REQUIRED"})
    void test_paidOrAuthorizedKeepsOpen(Status decision) {
        var fixture = data.mockWithProjection();
        long orderId = orderId(fixture);
        terminations.mockDecision(orderId, decision);

        assertOnly(scan(), ExpireOrderResponse.valueOf(decision.name()));

        checker.assertNotExpired(orderId, OrderStatus.OPEN, true);
        checker.assertSessionStatus(orderId, CheckoutSessionStatus.OPEN);
        checker.assertPaymentStatus(orderId, PaymentIntentStatus.REQUIRES_PAYMENT);
        webhooks.assertNoTask(eventId(fixture));
        assertEquals(1, terminations.calls().size());
    }

    /** Payment 503：返回待重试，订单保持 OPEN，除终止意图外不写入。 */
    @Test
    void test_paymentUnavailableRetries() {
        var fixture = data.mockWithProjection();
        long orderId = orderId(fixture);
        terminations.mockTerminationFault(orderId, problem(HttpStatus.SERVICE_UNAVAILABLE, CommonErrorCode.SERVICE_UNAVAILABLE));

        assertOnly(scan(), ExpireOrderResponse.RETRY);

        checker.assertNotExpired(orderId, OrderStatus.OPEN, true);
        checker.assertPaymentStatus(orderId, PaymentIntentStatus.REQUIRES_PAYMENT);
        webhooks.assertNoTask(eventId(fixture));
    }

    /** 已过期订单：Cancel 返回 409 ORDER_NOT_CANCELLABLE，消费者 GET 收银台与提交支付均返回 410 CHECKOUT_EXPIRED。 */
    @Test
    void test_expiredOrderRejectsCancelCheckoutAndPayment() {
        var fixture = data.mockOpen();
        assertOnly(scan(), ExpireOrderResponse.EXPIRED);

        cancel(fixture).assertProblem(409, CommerceCheckoutErrorCode.ORDER_NOT_CANCELLABLE.getCode());
        getCheckout(fixture).assertProblem(410, CustomerCheckoutErrorCode.CHECKOUT_EXPIRED.getCode());
        submitPayment(fixture).assertProblem(410, CustomerCheckoutErrorCode.CHECKOUT_EXPIRED.getCode());
        checker.assertExpired(orderId(fixture), true, NOW);
        terminations.assertNoCalls();
    }

    /** 重复扫描与直接重放：EXPIRED 只写一次，order.expired 只登记、提交一次，不再调用 Payment。 */
    @Test
    void test_repeatedScanWritesOnce() {
        var fixture = data.mockWithProjection();
        long orderId = orderId(fixture);
        terminations.mockDecision(orderId, Status.TERMINATED);
        assertOnly(scan(), ExpireOrderResponse.EXPIRED);
        webhooks.awaitSubmissions(eventId(fixture), 1);
        webhooks.awaitTaskCompleted(eventId(fixture));
        long version = checker.order(orderId).getVersion();

        adjustableUtcClock.advance(Duration.ofHours(1));
        assertEquals(0, scan().getScannedCount());
        assertEquals(ExpireOrderResponse.SKIPPED, expireDirectly(orderId));

        checker.assertExpired(orderId, true, NOW);
        assertEquals(version, checker.order(orderId).getVersion());
        assertEquals(1, terminations.calls().size());
        webhooks.assertNoTask(eventId(fixture));
        assertEquals(1, webhooks.submissions(eventId(fixture)).size());
    }

    /** 先取消后扫描：已取消订单不被选中，直接收尾也跳过，取消事实不被改写。 */
    @Test
    void test_cancelledBeforeScanNotRewritten() {
        var fixture = data.mockOpen();
        long orderId = orderId(fixture);
        cancel(fixture).assertCancelled(fixture.getOrder().getOrderNo(), true, NOW);
        long version = checker.order(orderId).getVersion();

        assertEquals(0, scan().getScannedCount());
        assertEquals(ExpireOrderResponse.SKIPPED, expireDirectly(orderId));

        checker.assertNotExpired(orderId, OrderStatus.CANCELLED, true);
        checker.assertReasonCode(orderId, ClientWebhookEvent.CLIENT_REQUESTED_REASON);
        assertEquals(version, checker.order(orderId).getVersion());
        webhooks.assertNoTask(eventId(fixture));
        terminations.assertNoCalls();
    }

    /** 另一实例持有订单锁：本轮计为跳过且不写；锁释放后下一轮收尾。 */
    @Test
    void test_lockedOrderSkipped() {
        var fixture = data.mockOpen();
        long orderId = orderId(fixture);
        var lock = redisson.getSpinLock("lock:" + Locks.getOrderLock(orderId));
        lock.lock();
        try {
            // 本线程持有可重入锁，扫描须在另一线程执行才能观察竞争。
            assertOnly(CompletableFuture.supplyAsync(this::scan).join(), ExpireOrderResponse.SKIPPED);
            checker.assertNotExpired(orderId, OrderStatus.OPEN, false);
        } finally {
            lock.unlock();
        }

        assertOnly(scan(), ExpireOrderResponse.EXPIRED);
        checker.assertExpired(orderId, true, NOW);
    }

    /** 未到期或已支付的订单不被扫描选中，直接收尾也跳过，均不调用 Payment。 */
    @Test
    void test_notDueOrPaidSkipped() {
        var notDue = data.mock(data.template().ready(), NOW.plusSeconds(60));
        var paid = data.mock(data.template().paid(), DUE_AT);

        assertEquals(0, scan().getScannedCount());
        assertEquals(ExpireOrderResponse.SKIPPED, expireDirectly(orderId(notDue)));
        assertEquals(ExpireOrderResponse.SKIPPED, expireDirectly(orderId(paid)));

        checker.assertNotExpired(orderId(notDue), OrderStatus.OPEN, false);
        checker.assertNotExpired(orderId(paid), OrderStatus.PAID, false);
        terminations.assertNoCalls();
    }

    /** 一单失败只计数，不影响同批其他订单收尾；失败订单只保留终止意图。 */
    @Test
    void test_singleFailureDoesNotStopScan() {
        var failing = data.mockWithProjection();
        var open = data.mockOpen();
        terminations.mockTerminationFault(orderId(failing),
            problem(HttpStatus.BAD_REQUEST, CommonErrorCode.BUSINESS_CHECK_FAILED));

        var response = scan();

        assertAll(() -> assertEquals(2, response.getScannedCount()), () -> assertEquals(1, response.getFailedCount()),
            () -> assertEquals(1, response.count(ExpireOrderResponse.EXPIRED)));
        checker.assertNotExpired(orderId(failing), OrderStatus.OPEN, true);
        checker.assertExpired(orderId(open), true, NOW);
    }

    /** 分页按配置的每页条数与最多页数截断，剩余订单留待下一轮。 */
    @Test
    void test_pagingStopsAtMaxBatches() {
        var first = data.mockOpen();
        var second = data.mockOpen();

        assertOnly(scan(1, 1), ExpireOrderResponse.EXPIRED);
        assertOnly(scan(1, 5), ExpireOrderResponse.EXPIRED);

        checker.assertExpired(orderId(first), true, NOW);
        checker.assertExpired(orderId(second), true, NOW);
    }

    /** 分片不命中：order_id 取模不等于本分片序号的订单不被选中、不调用 Payment；所属分片下一轮收尾。 */
    @Test
    void test_shardMismatchNotProcessed() {
        var fixture = data.mockWithProjection();
        long orderId = orderId(fixture);
        int ownShard = (int) (orderId % 2);

        assertEquals(0, job.runOnce(1 - ownShard, 2).getScannedCount());
        checker.assertNotExpired(orderId, OrderStatus.OPEN, false);
        terminations.assertNoCalls();

        terminations.mockDecision(orderId, Status.TERMINATED);
        assertOnly(job.runOnce(ownShard, 2), ExpireOrderResponse.EXPIRED);
        checker.assertExpired(orderId, true, NOW);
    }

    /** PENDING 超过默认 24 小时阈值：每轮记一条含 orderId、intentId 的 WARN，订单保持 OPEN；恰好 24 小时及之前不记。 */
    @Test
    void test_pendingBeyondThresholdWarns(CapturedOutput output) {
        var fixture = data.mockWithProjection();
        long orderId = orderId(fixture);
        terminations.mockDecision(orderId, Status.PENDING);

        assertOnly(scan(), ExpireOrderResponse.PENDING);
        adjustableUtcClock.advance(Duration.ofHours(24));
        assertOnly(scan(), ExpireOrderResponse.PENDING);
        assertEquals(0, pendingAlerts(output, orderId), "未超过阈值不告警");

        adjustableUtcClock.advance(Duration.ofMinutes(1));
        assertOnly(scan(), ExpireOrderResponse.PENDING);
        assertEquals(1, pendingAlerts(output, orderId), "超过阈值告警一次");
        adjustableUtcClock.advance(Duration.ofMinutes(1));
        assertOnly(scan(), ExpireOrderResponse.PENDING);
        assertEquals(2, pendingAlerts(output, orderId), "每轮仍 PENDING 都告警");

        checker.assertNotExpired(orderId, OrderStatus.OPEN, true);
        assertEquals(NOW, checker.order(orderId).getTerminationRequestedAt(), "终止意图保留首次时刻");
        webhooks.assertNoTask(eventId(fixture));
        assertEquals(4, terminations.calls().size());
    }

    /** 告警阈值来自配置：调为 1 小时后，终止意图写入 1 小时后仍 PENDING 即告警。 */
    @Test
    void test_pendingAlertThresholdConfigurable(CapturedOutput output) {
        config.mockPendingAlertAfter(Duration.ofHours(1));
        var fixture = data.mockWithProjection();
        long orderId = orderId(fixture);
        terminations.mockDecision(orderId, Status.PENDING);
        assertOnly(scan(), ExpireOrderResponse.PENDING);

        adjustableUtcClock.advance(Duration.ofMinutes(61));
        assertOnly(scan(), ExpireOrderResponse.PENDING);

        assertEquals(1, pendingAlerts(output, orderId));
        checker.assertNotExpired(orderId, OrderStatus.OPEN, true);
    }

    /** XXL 入口带分片上下文：按上下文分片扫描并收尾，任务结果为成功。 */
    @Test
    void test_xxlEntryUsesShardContext() {
        var fixture = data.mockOpen();
        long orderId = orderId(fixture);
        int ownShard = (int) (orderId % 2);

        assertEquals(XxlJobContext.HANDLE_CODE_SUCCESS, runXxl(1 - ownShard, 2).getHandleCode());
        checker.assertNotExpired(orderId, OrderStatus.OPEN, false);

        assertEquals(XxlJobContext.HANDLE_CODE_SUCCESS, runXxl(ownShard, 2).getHandleCode());
        checker.assertExpired(orderId, true, NOW);
    }

    /** XXL 入口没有调度上下文（例如本地直接触发）：按单分片全量扫描并收尾。 */
    @Test
    void test_xxlEntryWithoutContextScansAll() {
        var fixture = data.mockOpen();

        job.scan();

        checker.assertExpired(orderId(fixture), true, NOW);
        webhooks.awaitSubmissions(eventId(fixture), 1);
    }

    /** XXL 入口分片参数非法：扫描被拒，任务标记失败且不抛出，订单不变。 */
    @Test
    void test_xxlEntryInvalidShardFails() {
        var fixture = data.mockOpen();

        var context = runXxl(2, 2);

        assertAll(() -> assertEquals(XxlJobContext.HANDLE_CODE_FAIL, context.getHandleCode()),
            () -> assertTrue(context.getHandleMsg().contains("InvalidParameterException"), context.getHandleMsg()));
        checker.assertNotExpired(orderId(fixture), OrderStatus.OPEN, false);
        terminations.assertNoCalls();
    }

    /** XXL 入口有订单收尾失败：同批其他订单照常收尾，任务标记失败并带各结果条数。 */
    @Test
    void test_xxlEntryOrderFailureMarksJobFailed() {
        var failing = data.mockWithProjection();
        var open = data.mockOpen();
        terminations.mockTerminationFault(orderId(failing),
            problem(HttpStatus.BAD_REQUEST, CommonErrorCode.BUSINESS_CHECK_FAILED));

        var context = runXxl(0, 1);

        assertAll(() -> assertEquals(XxlJobContext.HANDLE_CODE_FAIL, context.getHandleCode()),
            () -> assertTrue(context.getHandleMsg().contains("scanned 2, expired 1"), context.getHandleMsg()),
            () -> assertTrue(context.getHandleMsg().endsWith("failed 1"), context.getHandleMsg()));
        checker.assertNotExpired(orderId(failing), OrderStatus.OPEN, true);
        checker.assertExpired(orderId(open), true, NOW);
    }

    /** 扫描响应只有一单且结果为期望值。 */
    static void assertOnly(ScanExpiredOrdersResponse response, ExpireOrderResponse expected) {
        assertAll(() -> assertEquals(1, response.getScannedCount(), "扫描订单数"),
            () -> assertEquals(0, response.getFailedCount(), "失败订单数"),
            () -> assertEquals(1, response.count(expected), "结果 " + expected));
    }

    /** 经 Handler 单分片、按配置分页扫描本轮时刻到期的订单。 */
    ScanExpiredOrdersResponse scan() {
        return job.runOnce(0, 1);
    }

    /** 本例改用指定分页配置后经 Handler 单分片扫描。 */
    ScanExpiredOrdersResponse scan(int batchSize, int maxBatches) {
        config.mockPaging(batchSize, maxBatches);
        return job.runOnce(0, 1);
    }

    /** 直接收尾一单，模拟另一实例的重复处理。 */
    ExpireOrderResponse expireDirectly(long orderId) {
        return expirer.process(new ExpireOrderRequest(orderId, adjustableUtcClock.instant(), config.pendingAlertAfter()));
    }

    /** 以指定分片的 XXL 上下文执行入口方法，返回执行后的上下文；测试线程的上下文随即清除。 */
    XxlJobContext runXxl(int shardIndex, int shardTotal) {
        var context = new XxlJobContext(1, null, 1, System.currentTimeMillis(), null, shardIndex, shardTotal);
        XxlJobContext.setXxlJobContext(context);
        try {
            job.scan();
        } finally {
            XxlJobContext.setXxlJobContext(null);
        }
        return context;
    }

    /** 已捕获输出中本订单的 PENDING 超阈值告警条数。 */
    static long pendingAlerts(CapturedOutput output, long orderId) {
        return output.getOut().lines()
            .filter(line -> line.contains(" WARN ") && line.contains(PENDING_ALERT) && line.contains("orderId=" + orderId + ","))
            .count();
    }

    /** Client 取消入口。 */
    CancelOrderResponseChecker cancel(Fixture fixture) {
        var headers = TestRequestHeaders.client(REQUEST_ID, Long.toString(clientId()), "1");
        var path = UriComponentsBuilder.fromPath("/client-gateway-api/v1/orders/{orderNo}/cancel")
            .buildAndExpand(fixture.getOrder().getOrderNo()).toUriString();
        return new CancelOrderResponseChecker(httpTestInvoker.post(path, headers, null), objectMapper, REQUEST_ID);
    }

    /** Client 订单查询入口。 */
    GetOrderResponseChecker getOrder(Fixture fixture) {
        var headers = TestRequestHeaders.client(REQUEST_ID, Long.toString(clientId()), "1");
        var path = UriComponentsBuilder.fromPath("/client-gateway-api/v1/orders/{orderNo}")
            .buildAndExpand(fixture.getOrder().getOrderNo()).toUriString();
        return new GetOrderResponseChecker(httpTestInvoker.get(path, headers), objectMapper, REQUEST_ID);
    }

    /** 消费者收银台查询入口。 */
    CheckoutFlowResponseChecker getCheckout(Fixture fixture) {
        return new CheckoutFlowResponseChecker(httpTestInvoker.get(checkoutPath(fixture), customerHeaders()),
            objectMapper, REQUEST_ID);
    }

    /** 消费者支付提交入口。 */
    CheckoutFlowResponseChecker submitPayment(Fixture fixture) {
        return new CheckoutFlowResponseChecker(httpTestInvoker.post(checkoutPath(fixture) + "/payments",
            customerHeaders(), SubmitPaymentRequestBuilder.forContract(orderId(fixture))), objectMapper, REQUEST_ID);
    }

    /** 本例会话的公开收银台路径。 */
    static String checkoutPath(Fixture fixture) {
        return "/customer-gateway-api/v1/checkouts/" + fixture.getSession().getPublicToken();
    }

    /** 消费者入口只需请求追踪头。 */
    static HttpHeaders customerHeaders() {
        var headers = new HttpHeaders();
        headers.set(ApiHeaderNames.REQUEST_ID, REQUEST_ID);
        return headers;
    }

    /** Payment 合同的 Problem 故障。 */
    static MockHttpFault problem(HttpStatus status, CommonErrorCode code) {
        return MockHttpFault.json(status.value(), new ApiProblemResponse(ApiProblemProfile.TYPE,
            status.getReasonPhrase(), "Payment 终止失败", code.getCode(), "payment-test",
            "2026-09-08T00:00:00.000000Z", null));
    }

    /** 本例 Client。 */
    long clientId() {
        return checkoutDataMocker.clientId();
    }

    /** 本例订单 ID。 */
    static long orderId(Fixture fixture) {
        return fixture.getOrder().getOrderId();
    }

    /** 本例订单的 order.expired 事件 ID。 */
    static String eventId(Fixture fixture) {
        return ClientWebhookEventType.ORDER_EXPIRED.eventId(fixture.getOrder().getOrderNo());
    }
}
