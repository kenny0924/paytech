package com.paytech.acquiring.auto.ext.job;

import static com.paytech.acquiring.test.builder.order.GetOrderDataBuilder.NOW;
import static com.paytech.acquiring.test.mocker.external.PlatformWebhookMocker.accepted;
import static com.paytech.acquiring.test.mocker.external.PlatformWebhookMocker.disconnect;
import static com.paytech.acquiring.test.mocker.external.PlatformWebhookMocker.problem;
import static com.paytech.acquiring.test.mocker.external.PlatformWebhookMocker.raw;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.acquiring.core.dal.dataobject.refund.RefundRequest;
import com.paytech.acquiring.domain.enums.ClientWebhookEventType;
import com.paytech.acquiring.domain.enums.OrderPaymentStatus;
import com.paytech.acquiring.domain.enums.OrderStatus;
import com.paytech.acquiring.domain.enums.RefundRequestOrigin;
import com.paytech.acquiring.domain.enums.RefundStatus;
import com.paytech.acquiring.domain.webhook.ClientWebhookEvent;
import com.paytech.acquiring.domainservice.port.CommerceIdentifierGenerator;
import com.paytech.acquiring.ext.job.ClientWebhookSubmissionHandler;
import com.paytech.acquiring.test.base.MockTestBase;
import com.paytech.acquiring.test.builder.order.GetOrderDataBuilder.Fixture;
import com.paytech.acquiring.test.checker.webhook.ClientWebhookChecker;
import com.paytech.acquiring.test.dal.ClientWebhookTestMapper;
import com.paytech.acquiring.test.mocker.external.PlatformWebhookMocker.Reply;
import com.paytech.acquiring.test.mocker.external.PlatformWebhookMocker.Submission;
import com.paytech.acquiring.test.mocker.local.OrderQueryDataMocker;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.compensation.api.CompensationEventRegistrar;
import com.paytech.foundation.compensation.api.CompensationEventScanner;
import com.paytech.foundation.compensation.api.CompensationScanRequest;
import com.paytech.foundation.httpproblem.api.ProblemFieldError;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 冻结事件经真实 Dispatcher / Scanner 提交 Platform：结果映射、冻结内容重放、载荷损坏与登记大小上限。
 * <li>事件由 Handler 的工厂在测试事务内登记，提交后由 Foundation 唤醒；重试通过真实扫描入口唤醒到期任务。</li>
 *
 * @see ClientWebhookSubmissionHandler#handle(com.paytech.foundation.compensation.api.CompensationEvent)
 * @see ClientWebhookSubmissionHandler#buildEvent(ClientWebhookEvent)
 */
class ClientWebhookSubmissionTest extends MockTestBase {
    private static final CompensationScanRequest SCAN =
        new CompensationScanRequest(100, 0, 1, Set.of(ClientWebhookSubmissionHandler.MAIN_TYPE));

    @Autowired
    private OrderQueryDataMocker orders;
    @Autowired
    private ClientWebhookChecker webhooks;
    @Autowired
    private ClientWebhookTestMapper mapper;
    @Autowired
    private CompensationEventScanner scanner;
    @Autowired
    private CompensationEventRegistrar registrar;
    @Autowired
    private CommerceIdentifierGenerator identifiers;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private Fixture fixture;

    /** 已支付订单，作为 order.paid 事件来源。 */
    @BeforeEach
    void setUpSubmission() {
        fixture = orders.template().paid().build();
        orders.mock(fixture);
    }

    /** Platform 暂不可用（503 或断连）可重试，扫描唤醒后以相同冻结内容提交成功并删除任务。 */
    @ParameterizedTest
    @MethodSource("unavailableReplies")
    void test_retryableError(Reply unavailable) {
        platformWebhookMocker.replies(eventId(), unavailable, accepted());

        register();

        webhooks.assertTask(webhooks.awaitTask(eventId(), "ERROR"), "ERROR", 1, "PLATFORM_UNAVAILABLE");
        var first = webhooks.submissions(eventId()).getFirst();
        webhooks.assertFrozen(first, ClientWebhookEventType.ORDER_PAID, eventId(), checkoutDataMocker.clientId(),
            NOW.toString(), expectedObject());
        var second = scanUntilSubmissions(2);
        webhooks.awaitTaskCompleted(eventId());
        webhooks.assertSameContent(first, second);
    }

    /** 404（Webhook 未开启或路由缺失）与无法识别的响应按暂不可用重试。 */
    @ParameterizedTest
    @MethodSource("retryableRejections")
    void test_retryableRejection(Reply reply, String errorCode) {
        platformWebhookMocker.replies(eventId(), reply);

        register();

        webhooks.assertTask(webhooks.awaitTask(eventId(), "ERROR"), "ERROR", 1, errorCode);
        scanUntilSubmissions(2);
        webhooks.awaitTaskCompleted(eventId());
    }

    /** Platform 明确拒绝（400 业务或参数、409 内容冲突、422 字段校验）记为明确失败，扫描不再提交。 */
    @ParameterizedTest
    @MethodSource("terminalRejections")
    void test_businessRuleViolation(Reply reply, String errorCode) {
        platformWebhookMocker.replies(eventId(), reply);

        register();

        webhooks.assertTask(webhooks.awaitTask(eventId(), "FAILED"), "FAILED", 1, errorCode);
        scanner.scan(SCAN);
        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(3))
            .until(() -> webhooks.submissions(eventId()).size() == 1);
        webhooks.assertTask(mapper.task(eventId()), "FAILED", 1, errorCode);
    }

    /** 首次提交失败后订单又被修改，补交仍是登记时冻结的内容。 */
    @Test
    void test_frozenContentAfterOrderChanged() {
        platformWebhookMocker.replies(eventId(), problem(HttpStatus.SERVICE_UNAVAILABLE,
            CommonErrorCode.SERVICE_UNAVAILABLE.getCode()), accepted());
        register();
        webhooks.awaitTask(eventId(), "ERROR");

        assertEquals(1, mapper.changeOrder(fixture.getOrder().getOrderId(), "changed-after-paid"));

        var second = scanUntilSubmissions(2);
        webhooks.awaitTaskCompleted(eventId());
        webhooks.assertSameContent(webhooks.submissions(eventId()).getFirst(), second);
        webhooks.assertFrozen(second, ClientWebhookEventType.ORDER_PAID, eventId(), checkoutDataMocker.clientId(),
            NOW.toString(), expectedObject());
    }

    /** 冻结载荷被篡改后 Hash 不一致：明确失败 WEBHOOK_PAYLOAD_CORRUPTED，不再调用 Platform。 */
    @Test
    void test_corruptedPayload() {
        platformWebhookMocker.replies(eventId(), problem(HttpStatus.SERVICE_UNAVAILABLE,
            CommonErrorCode.SERVICE_UNAVAILABLE.getCode()));
        register();
        var task = webhooks.awaitTask(eventId(), "ERROR");
        var payload = (ObjectNode)objectMapper.readTree((String)task.get("payload_json"));
        ((ObjectNode)payload.path("envelope").path("data").path("object")).put("clientOrderId", "tampered");

        assertEquals(1, mapper.replacePayload(eventId(), objectMapper.writeValueAsString(payload)));

        scanUntilTask("FAILED");
        webhooks.assertTask(mapper.task(eventId()), "FAILED", 2, ClientWebhookSubmissionHandler.PAYLOAD_CORRUPTED);
        assertEquals(1, webhooks.submissions(eventId()).size(), "载荷损坏不能提交 Platform");
    }

    /** 人工插入的无载荷任务不符合登记合同：明确失败，不调用 Platform。 */
    @Test
    void test_invalidParameter() {
        mapper.insertTaskWithoutPayload(identifiers.nextId(), eventId(), ClientWebhookEventType.ORDER_PAID.getCode());

        scanUntilTask("FAILED");

        webhooks.assertTask(mapper.task(eventId()), "FAILED", 1, "INVALID_WEBHOOK_TASK");
        assertTrue(webhooks.submissions(eventId()).isEmpty());
    }

    /** 规范化后超过 4096 字节的事件在登记前抛出系统异常，使调用方业务事务回滚。 */
    @Test
    void test_edgeCase() {
        var refund = RefundRequest.builder().refundRequestNo("rfd_oversized").clientId(checkoutDataMocker.clientId())
            .requestOrigin(RefundRequestOrigin.CLIENT).clientRefundId("R".repeat(4096)).refundAmount(BigDecimal.TEN)
            .currency("USD").reasonCode("OTHER").refundStatus(RefundStatus.PENDING).version(1).createTime(NOW)
            .updateTime(NOW).build();
        var event = ClientWebhookEvent.refundCreated(refund, fixture.getOrder().getOrderNo());

        assertThrows(ErrorException.class, () -> ClientWebhookSubmissionHandler.buildEvent(event));
    }

    /** 在测试事务内经 Handler 工厂登记 order.paid，提交后由 Foundation 唤醒执行。 */
    void register() {
        var event = ClientWebhookEvent.orderPaid(fixture.getOrder(), fixture.getOrder().getTotalAmount(), NOW);
        new TransactionTemplate(transactionManager)
            .executeWithoutResult(status -> registrar.register(ClientWebhookSubmissionHandler.buildEvent(event)));
    }

    /** 反复经真实 Scanner 唤醒到期任务（首次退避约 1 秒），直到累计提交到指定次数。 */
    Submission scanUntilSubmissions(int count) {
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(300)).until(() -> {
            scanner.scan(SCAN);
            return webhooks.submissions(eventId()).size() >= count;
        });
        return webhooks.awaitSubmissions(eventId(), count);
    }

    /** 反复唤醒到期任务，直到任务进入指定状态且不再持有 Lease。 */
    void scanUntilTask(String status) {
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(300)).until(() -> {
            scanner.scan(SCAN);
            var task = mapper.task(eventId());
            return task != null && status.equals(task.get("execute_status")) && task.get("lock_owner") == null;
        });
    }

    /** 冻结时的 order.paid 对象，来自已支付订单 Fixture。 */
    JsonNode expectedObject() {
        var order = fixture.getOrder();
        var amounts = new LinkedHashMap<String, Object>();
        amounts.put("totalAmount", "50.00");
        amounts.put("paidAmount", "50.00");
        Map<String, Object> object = new LinkedHashMap<>();
        object.put("orderNo", order.getOrderNo());
        object.put("clientOrderId", order.getClientOrderId());
        object.put("orderStatus", OrderStatus.PAID.getCode());
        object.put("paymentStatus", OrderPaymentStatus.SUCCEEDED.getCode());
        object.put("currency", "USD");
        object.put("amounts", amounts);
        object.put("paidAt", order.getPaidAt().toString());
        object.put("version", order.getVersion());
        return objectMapper.valueToTree(object);
    }

    /** 本例订单的 order.paid 事件 ID。 */
    String eventId() {
        return ClientWebhookEventType.ORDER_PAID.eventId(fixture.getOrder().getOrderNo());
    }

    /** 暂不可用的两种表现。 */
    static Stream<Reply> unavailableReplies() {
        return Stream.of(problem(HttpStatus.SERVICE_UNAVAILABLE, CommonErrorCode.SERVICE_UNAVAILABLE.getCode()),
            disconnect());
    }

    /** 按暂不可用处理的 4xx 与无法识别的响应。 */
    static Stream<Arguments> retryableRejections() {
        return Stream.of(
            Arguments.of(problem(HttpStatus.NOT_FOUND, CommonErrorCode.NOT_FOUND.getCode()), "PLATFORM_NOT_FOUND"),
            Arguments.of(raw(HttpStatus.BAD_REQUEST, "<html>proxy</html>"), "PLATFORM_INVALID_RESPONSE"));
    }

    /** Platform 明确拒绝；错误码沿用 Platform 返回的公共代码。 */
    static Stream<Arguments> terminalRejections() {
        return Stream.of(
            Arguments.of(problem(HttpStatus.BAD_REQUEST, CommonErrorCode.BUSINESS_CHECK_FAILED.getCode()),
                CommonErrorCode.BUSINESS_CHECK_FAILED.getCode()),
            Arguments.of(problem(HttpStatus.BAD_REQUEST, CommonErrorCode.INVALID_REQUEST.getCode()),
                CommonErrorCode.INVALID_REQUEST.getCode()),
            Arguments.of(problem(HttpStatus.CONFLICT, CommonErrorCode.CONCURRENT_REQUEST.getCode()),
                CommonErrorCode.CONCURRENT_REQUEST.getCode()),
            Arguments.of(problem(HttpStatus.UNPROCESSABLE_CONTENT, CommonErrorCode.VALIDATION_ERROR.getCode(),
                List.of(new ProblemFieldError("envelope", "格式不合法"))), CommonErrorCode.VALIDATION_ERROR.getCode()));
    }
}
