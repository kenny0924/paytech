package com.paytech.platform.support.auto.webhook;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;
import com.paytech.platform.support.delivery.ext.eventhandler.WebhookDeliveryHandler;
import com.paytech.platform.support.api.enums.WebhookEventType;
import com.paytech.platform.support.delivery.core.enums.WebhookDeliveryStatus;
import com.paytech.platform.support.delivery.core.enums.WebhookAttemptStatus;
import com.paytech.platform.support.delivery.core.enums.WebhookFailureCode;
import com.paytech.foundation.compensation.model.CompensationEventStatus;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.platform.support.test.base.WebhookTestBase;
import com.paytech.platform.support.test.webhook.WebhookEventBuilder;
import com.paytech.platform.support.test.webhook.WebhookFixture;
import com.paytech.platform.support.test.webhook.WebhookSignatures;

/**
 * 投递执行：每次补偿执行做一次 HTTP 尝试，按结果进入 SUCCEEDED、RETRY_WAIT、DEAD 或 CANCELLED。
 * <li>事件经内部接口受理，首次执行由提交后唤醒；后续执行经正式 Scanner 触发</li>
 *
 * @see WebhookDeliveryHandler#handle(CompensationEvent)
 */
class WebhookDeliveryHandlerTest extends WebhookTestBase {

    @Autowired
    private WebhookDeliveryHandler handler;

    private static final String SUBSCRIBED = WebhookFixture.eventTypes(WebhookEventType.ORDER_PAID);

    /** 非 2xx（含 3xx）与断连都按失败重试；第 3 次（测试上限）失败记 DEAD，任务完成。 */
    @Test
    void test_retryUntilDead() {
        fixture.endpoint(WebhookFixture.TENANT_ID, fixture.receiverUrl("a"), SUBSCRIBED);
        receiver.reply("a", 500).reply("a", 302).disconnect("a");

        long deliveryId = submitOrderPaid("evt_dead_001");
        assertDelivery(deliveryId, WebhookDeliveryStatus.RETRY_WAIT, 1);
        assertEquals(CompensationEventStatus.ERROR.getCode(), webhookData.tasks().getFirst().get("execute_status"));
        fixture.retryDue();
        assertDelivery(deliveryId, WebhookDeliveryStatus.RETRY_WAIT, 2);
        fixture.retryDue();

        assertDelivery(deliveryId, WebhookDeliveryStatus.DEAD, 3);
        assertEquals(0, webhookData.tasks().size(), "DEAD 是正常终态，补偿任务完成");
        var attempts = webhookData.attempts(deliveryId);
        assertAttempt(attempts.get(0), WebhookAttemptStatus.FAILED, 500, WebhookFailureCode.HTTP_STATUS);
        assertAttempt(attempts.get(1), WebhookAttemptStatus.FAILED, 302, WebhookFailureCode.HTTP_STATUS);
        assertAttempt(attempts.get(2), WebhookAttemptStatus.FAILED, null, WebhookFailureCode.CONNECT_FAILED);
        assertEquals(3, receiver.requests("a").size());
    }

    /** 失败后重试成功。 */
    @Test
    void test_retryThenSucceed() {
        fixture.endpoint(WebhookFixture.TENANT_ID, fixture.receiverUrl("a"), SUBSCRIBED);
        receiver.reply("a", 503).reply("a", 204);

        long deliveryId = submitOrderPaid("evt_retry_001");
        fixture.retryDue();

        assertDelivery(deliveryId, WebhookDeliveryStatus.SUCCEEDED, 2);
        assertAttempt(webhookData.attempts(deliveryId).get(1), WebhookAttemptStatus.SUCCEEDED, 204, null);
        assertEquals(0, webhookData.tasks().size());
    }

    /** 等待响应超过超时：记 TIMEOUT 并等待重试。 */
    @Test
    void test_timeout() {
        fixture.endpoint(WebhookFixture.TENANT_ID, fixture.receiverUrl("slow"), SUBSCRIBED);
        receiver.replyAfter("slow", 1500);

        long deliveryId = submitOrderPaid("evt_timeout_001");

        assertDelivery(deliveryId, WebhookDeliveryStatus.RETRY_WAIT, 1);
        assertAttempt(webhookData.attempts(deliveryId).getFirst(), WebhookAttemptStatus.FAILED, null, WebhookFailureCode.TIMEOUT);
    }

    /** 端点停用后，未完成的投递在下一次执行时取消，不再发送。 */
    @Test
    void test_disabledEndpointCancels() {
        var endpoint = fixture.endpoint(WebhookFixture.TENANT_ID, fixture.receiverUrl("a"), SUBSCRIBED);
        receiver.reply("a", 500);
        long deliveryId = submitOrderPaid("evt_cancel_001");

        webhookData.disable(endpoint.endpointId());
        fixture.retryDue();

        assertDelivery(deliveryId, WebhookDeliveryStatus.CANCELLED, 1);
        assertEquals(1, receiver.requests("a").size());
        assertEquals(0, webhookData.tasks().size());
    }

    /** 宽限期内的旧密钥参与签名：Header 带两个 v1，ACTIVE 在前；尝试记录宽限密钥。 */
    @Test
    void test_graceSecretDualSignature() {
        var endpoint = fixture.endpoint(WebhookFixture.TENANT_ID, fixture.receiverUrl("a"), SUBSCRIBED);
        String grace = fixture.graceSecret(endpoint.endpointId(), clock.instant().plus(Duration.ofMinutes(30)));
        receiver.reply("a", 200);

        long deliveryId = submitOrderPaid("evt_grace_001");

        var request = receiver.requests("a").getFirst();
        var signature = WebhookSignatures.parse(request.signature());
        assertEquals(List.of(WebhookSignatures.expected(endpoint.secret(), signature.timestamp(), request.body()),
            WebhookSignatures.expected(grace, signature.timestamp(), request.body())), signature.signatures());
        assertNotNull(webhookData.attempts(deliveryId).getFirst().get("grace_secret_id"));
    }

    /** 宽限已过期的旧密钥不再参与签名。 */
    @Test
    void test_expiredGraceSecret() {
        var endpoint = fixture.endpoint(WebhookFixture.TENANT_ID, fixture.receiverUrl("a"), SUBSCRIBED);
        fixture.graceSecret(endpoint.endpointId(), clock.instant().minusSeconds(1));
        receiver.reply("a", 200);

        long deliveryId = submitOrderPaid("evt_grace_002");

        assertEquals(1, WebhookSignatures.parse(receiver.requests("a").getFirst().signature()).signatures().size());
        assertNull(webhookData.attempts(deliveryId).getFirst().get("grace_secret_id"));
    }

    /** Vault 不可用：不外发、不计尝试，任务等待重试；恢复后正常发送。 */
    @Test
    void test_secretUnavailable() {
        fixture.endpoint(WebhookFixture.TENANT_ID, fixture.receiverUrl("a"), SUBSCRIBED);
        vault.revealUnavailable(true);

        long deliveryId = submitOrderPaid("evt_vault_001");
        assertDelivery(deliveryId, WebhookDeliveryStatus.CREATED, 0);
        assertEquals(WebhookDeliveryHandler.SECRET_UNAVAILABLE, webhookData.tasks().getFirst().get("last_error_code"));
        assertEquals(0, receiver.count());

        vault.revealUnavailable(false);
        receiver.reply("a", 200);
        fixture.retryDue();
        assertDelivery(deliveryId, WebhookDeliveryStatus.SUCCEEDED, 1);
    }

    /** 上次执行在发送中中断：遗留尝试记 INTERRUPTED 并计入次数，随后重新发送。 */
    @Test
    void test_interruptedRecovery() {
        fixture.endpoint(WebhookFixture.TENANT_ID, fixture.receiverUrl("a"), SUBSCRIBED);
        receiver.reply("a", 500);
        long deliveryId = submitOrderPaid("evt_interrupt_001");
        webhookData.markProcessing(deliveryId);
        webhookData.processingAttempt(fixture.nextId(), deliveryId);
        receiver.reply("a", 200);

        fixture.retryDue();

        assertDelivery(deliveryId, WebhookDeliveryStatus.SUCCEEDED, 3);
        var attempts = webhookData.attempts(deliveryId);
        assertAttempt(attempts.get(1), WebhookAttemptStatus.FAILED, null, WebhookFailureCode.INTERRUPTED);
        assertAttempt(attempts.get(2), WebhookAttemptStatus.SUCCEEDED, 200, null);
    }

    /** 冻结的 Envelope 与摘要不一致：不发送，任务终止待人工处理。 */
    @Test
    void test_payloadIntegrity() {
        fixture.endpoint(WebhookFixture.TENANT_ID, fixture.receiverUrl("a"), SUBSCRIBED);
        receiver.reply("a", 500);
        long deliveryId = submitOrderPaid("evt_integrity_001");
        webhookData.tamperPayloads();

        fixture.retryDue();

        assertDelivery(deliveryId, WebhookDeliveryStatus.RETRY_WAIT, 1);
        assertEquals(1, receiver.requests("a").size());
        var task = webhookData.tasks().getFirst();
        assertEquals(CompensationEventStatus.FAILED.getCode(), task.get("execute_status"));
        assertEquals(WebhookDeliveryHandler.PAYLOAD_INTEGRITY, task.get("last_error_code"));
    }

    /** 端点解析到未放行的内网地址：不建立连接，记 BLOCKED_ADDRESS。 */
    @Test
    void test_blockedAddress() {
        fixture.endpoint(WebhookFixture.TENANT_ID, "http://10.0.0.1/hook", SUBSCRIBED);

        long deliveryId = submitOrderPaid("evt_blocked_001");

        assertDelivery(deliveryId, WebhookDeliveryStatus.RETRY_WAIT, 1);
        assertAttempt(webhookData.attempts(deliveryId).getFirst(), WebhookAttemptStatus.FAILED, null, WebhookFailureCode.BLOCKED_ADDRESS);
    }

    /** 两个执行者同时推进同一投递：投递版本 CAS 只让一个发出请求，另一个保留任务等待下一轮。 */
    @Test
    void test_concurrentExecution() throws Exception {
        fixture.endpoint(WebhookFixture.TENANT_ID, fixture.receiverUrl("a"), SUBSCRIBED);
        receiver.reply("a", 500);
        long deliveryId = submitOrderPaid("evt_concurrent_001");
        receiver.reply("a", 200);
        var event = new CompensationEvent(1, Long.toString(deliveryId), handler.eventType(), "-", null, 2);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> { start.await(); return handler.handle(event); });
            var second = pool.submit(() -> { start.await(); return handler.handle(event); });
            start.countDown();
            var outcomes = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertTrue(outcomes.stream().anyMatch(result -> result.outcome() == CompensationHandlerResult.Outcome.SUCCESS));
        }

        assertDelivery(deliveryId, WebhookDeliveryStatus.SUCCEEDED, 2);
        assertEquals(2, receiver.requests("a").size(), "首次失败一次，并发推进只再发一次");
    }

    /** 任务身份不合法：终止任务，不读取投递。 */
    @Test
    void test_invalidTaskIdentity() {
        var result = handler.handle(new CompensationEvent(1, "not-a-delivery", handler.eventType(), "-", null, 1));

        assertEquals(CompensationHandlerResult.Outcome.TERMINAL_FAILURE, result.outcome());
        assertEquals(0, receiver.count());
    }

    /** 受理一个 order.paid 事件并等待首次执行结束，返回唯一投递。 */
    long submitOrderPaid(String eventId) {
        submit(WebhookFixture.CALLER, WebhookEventBuilder.orderPaid(eventId).build()).assertNoContent(204);
        fixture.awaitIdle();
        var deliveries = webhookData.deliveries();
        assertEquals(1, deliveries.size());
        return ((Number) deliveries.getFirst().get("delivery_id")).longValue();
    }

    /** 投递状态与尝试次数。 */
    void assertDelivery(long deliveryId, WebhookDeliveryStatus status, int attemptCount) {
        var delivery = webhookData.delivery(deliveryId);
        assertEquals(status.getCode(), delivery.get("status"));
        assertEquals(attemptCount, ((Number) delivery.get("attempt_count")).intValue());
        assertEquals(attemptCount, webhookData.attempts(deliveryId).size());
    }

    /** 尝试结果。 */
    static void assertAttempt(Map<String, Object> attempt, WebhookAttemptStatus status, Integer httpStatus,
        WebhookFailureCode failureCode) {
        assertEquals(status.getCode(), attempt.get("status"));
        assertEquals(httpStatus, attempt.get("http_status") == null ? null : ((Number) attempt.get("http_status")).intValue());
        assertEquals(failureCode == null ? null : failureCode.getCode(), attempt.get("failure_code"));
    }
}
