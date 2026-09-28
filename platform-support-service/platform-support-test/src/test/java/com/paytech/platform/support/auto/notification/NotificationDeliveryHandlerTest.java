package com.paytech.platform.support.auto.notification;

import static com.paytech.platform.support.delivery.integration.provider.EmailProviderResult.Outcome.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationEventScanner;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;
import com.paytech.foundation.compensation.api.CompensationScanRequest;
import com.paytech.platform.support.delivery.ext.eventhandler.NotificationDeliveryHandler;
import com.paytech.platform.support.delivery.service.processor.SubmitNotificationProcessor;
import com.paytech.platform.support.test.base.NotificationTestBase;
import com.paytech.platform.support.test.builder.NotificationRequestBuilder;

/**
 * 补偿 Handler 的实际 Dispatcher 重入、原发送查询与有界发送恢复。
 * @see NotificationDeliveryHandler#handle(CompensationEvent)
 */
class NotificationDeliveryHandlerTest extends NotificationTestBase {

    @Autowired SubmitNotificationProcessor processor;
    @Autowired CompensationEventScanner scanner;
    @Autowired NotificationDeliveryHandler handler;
    @Autowired java.time.Clock clock;

    /** UNKNOWN 查询继续原请求号，账号停用不影响恢复，不创建第二次发送。 */
    @Test
    void unknownQueriesOriginalAttemptWithoutResending() {
        provider.expectSend("alice@example.com", UNKNOWN);
        processor.process(NotificationRequestBuilder.template());
        fixture.awaitIdle();
        assertEquals("UNKNOWN", mapper.deliveries().getFirst().get("status"));
        String requestNo = provider.calls().getFirst().getRequestNo();
        provider.expectQuery(requestNo, DELIVERED);
        mapper.disableProvider();
        scanDue();
        assertEquals("DELIVERED", mapper.deliveries().getFirst().get("status"));
        assertEquals(1, mapper.attempts().size());
        assertEquals(1, ((Number) mapper.deliveries().getFirst().get("attempt_count")).intValue());
        assertEquals(1, provider.calls().size());
        assertEquals(java.util.List.of(requestNo), provider.queryCalls());
        assertEquals(1, vault.revealCount());
        assertTrue(mapper.events().isEmpty());
    }

    /** 明确关闭的失败才重试，重试读取当前文案并创建新请求号。 */
    @Test
    void definitiveRejectionRetriesWithCurrentContent() {
        provider.expectSend("alice@example.com", RETRYABLE_REJECTED, DELIVERED);
        processor.process(NotificationRequestBuilder.template());
        fixture.awaitIdle();
        assertEquals("RETRY_WAIT", mapper.deliveries().getFirst().get("status"));
        assertNotNull(mapper.deliveries().getFirst().get("next_retry_time"));
        mapper.changeContent("Updated ${name}");
        mapper.retryDue(clock.instant());
        scanDue();
        assertEquals(2, mapper.attempts().size());
        assertEquals(2, ((Number) mapper.deliveries().getFirst().get("attempt_count")).intValue());
        assertEquals("Updated Alice", provider.calls().get(1).getBody());
        assertNotEquals(provider.calls().get(0).getRequestNo(), provider.calls().get(1).getRequestNo());
        assertEquals("DELIVERED", mapper.deliveries().getFirst().get("status"));
        assertTrue(provider.queryCalls().isEmpty());
    }

    /** UNKNOWN 查询仍未知时只保留恢复任务，不错误地进入重发。 */
    @Test
    void unresolvedQueryRemainsUnknown() {
        provider.expectSend("alice@example.com", UNKNOWN);
        processor.process(NotificationRequestBuilder.template());
        fixture.awaitIdle();
        provider.expectQuery(provider.calls().getFirst().getRequestNo(), UNKNOWN);
        scanDue();
        assertEquals("UNKNOWN", mapper.deliveries().getFirst().get("status"));
        assertEquals(1, mapper.attempts().size());
        assertNull(mapper.deliveries().getFirst().get("next_retry_time"));
        assertEquals(1, mapper.events().size());
        assertNull(mapper.events().getFirst().get("payload_json"));
    }

    /** 终态重复事件只读返回，不再次发送或查询。 */
    @Test
    void terminalEventReentryDoesNothing() {
        provider.expectSend("alice@example.com", DELIVERED);
        processor.process(NotificationRequestBuilder.template());
        fixture.awaitIdle();
        String id = mapper.deliveries().getFirst().get("delivery_id").toString();
        assertEquals(CompensationHandlerResult.Outcome.SUCCESS,
            handler.handle(new CompensationEvent(1, id, handler.eventType(), "-", null, 2)).outcome());
        assertEquals(1, provider.calls().size());
        assertTrue(provider.queryCalls().isEmpty());
    }

    /** 查询接管后的版本保护拒绝旧响应，即便旧响应声称确定失败。 */
    @Test
    void queryTakeoverFencesLateSendResult() throws InterruptedException {
        provider.expectSend("alice@example.com", REJECTED);
        var pause = provider.pauseNextSend();
        try {
            processor.process(NotificationRequestBuilder.template());
            pause.awaitEntered();
            String requestNo = provider.calls().getFirst().getRequestNo();
            provider.expectQuery(requestNo, DELIVERED);
            String deliveryId = mapper.deliveries().getFirst().get("delivery_id").toString();
            assertEquals(CompensationHandlerResult.Outcome.SUCCESS,
                handler.handle(new CompensationEvent(1, deliveryId, handler.eventType(), "-", null, 2)).outcome());
        } finally {
            pause.release();
        }
        fixture.awaitIdle();
        assertEquals("DELIVERED", mapper.deliveries().getFirst().get("status"));
        assertEquals("ACCEPTED", mapper.attempts().getFirst().get("status"));
        assertEquals("PROVIDER_QUERY", mapper.attempts().getFirst().get("resolution_source"));
        assertEquals(1, provider.calls().size());
        scanDue();
        assertTrue(mapper.events().isEmpty());
    }

    /** 确定可重试失败达到实际发送预算后结束，不继续登记新尝试。 */
    @Test
    void boundsActualSendAttempts() {
        provider.expectSend("alice@example.com", RETRYABLE_REJECTED, RETRYABLE_REJECTED, RETRYABLE_REJECTED);
        processor.process(NotificationRequestBuilder.template());
        fixture.awaitIdle();
        for (int retry = 0; retry < 2; retry++) { mapper.retryDue(clock.instant()); scanDue(); }
        assertEquals("FAILED", mapper.deliveries().getFirst().get("status"));
        assertEquals(3, mapper.attempts().size());
        assertEquals(3, ((Number) mapper.deliveries().getFirst().get("attempt_count")).intValue());
        assertNull(mapper.deliveries().getFirst().get("next_retry_time"));
        assertTrue(mapper.events().isEmpty());
        assertEquals(3, provider.calls().size());
    }

    /** Provider 受理后只查询最终结果，未知查询不能恢复成待发送。 */
    @Test
    void acceptedSendKeepsItsFactAcrossUnknownQueries() {
        provider.expectSend("alice@example.com", ACCEPTED);
        processor.process(NotificationRequestBuilder.template());
        fixture.awaitIdle();
        provider.expectQuery(provider.calls().getFirst().getRequestNo(), UNKNOWN);
        scanDue();
        assertEquals("SENT", mapper.deliveries().getFirst().get("status"));
        assertEquals("ACCEPTED", mapper.attempts().getFirst().get("status"));
        assertEquals(1, mapper.attempts().size());
        assertEquals(1, provider.calls().size());
    }

    /** 明确关闭原请求后才允许换备用账号，尝试保留各自账号和请求号。 */
    @Test
    void definitiveRejectionCanUseFallbackAccount() {
        mapper.fallbackProvider(); mapper.useFallback();
        provider.expectSend("alice@example.com", RETRYABLE_REJECTED, DELIVERED);
        processor.process(NotificationRequestBuilder.template());
        fixture.awaitIdle();
        mapper.retryDue(clock.instant()); scanDue();
        assertEquals("DELIVERED", mapper.deliveries().getFirst().get("status"));
        assertEquals(2, mapper.attempts().size());
        assertEquals(890004L, ((Number) mapper.attempts().getFirst().get("provider_id")).longValue());
        assertEquals(890006L, ((Number) mapper.attempts().getLast().get("provider_id")).longValue());
        assertTrue(provider.calls().getLast().getRequestNo().startsWith("fallback_"));
        assertTrue(provider.queryCalls().isEmpty());
    }

    /** 当前模板新增旧请求没有的参数时明确结束，不能发送残缺正文或伪造新尝试。 */
    @Test
    void incompatibleCurrentContentFailsBeforeNewSend() {
        provider.expectSend("alice@example.com", RETRYABLE_REJECTED);
        processor.process(NotificationRequestBuilder.template());
        fixture.awaitIdle();
        mapper.changeContent("Required ${newParameter}");
        mapper.retryDue(clock.instant()); scanDue();
        assertEquals("FAILED", mapper.deliveries().getFirst().get("status"));
        assertEquals(1, mapper.attempts().size());
        assertEquals(1, provider.calls().size());
        assertTrue(mapper.events().isEmpty());
    }

    /** 只改变本例任务到期时间，后续 Claim、Handler 与完成回写全部走生产实现。 */
    void scanDue() {
        var previous = mapper.events();
        assertEquals(1, previous.size());
        int executeCount = ((Number) previous.getFirst().get("execute_count")).intValue();
        mapper.due();
        scanner.scan(new CompensationScanRequest(100, 0, 1, Set.of(handler.eventType())));
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(10)).until(() -> {
            var current = mapper.events();
            return current.isEmpty() || ((Number) current.getFirst().get("execute_count")).intValue() > executeCount;
        });
        fixture.awaitIdle();
    }
}
