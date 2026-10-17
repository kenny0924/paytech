package com.paytech.platform.support.auto.notification;

import static com.paytech.platform.support.delivery.integration.provider.EmailProviderResult.Outcome.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;
import com.paytech.platform.support.delivery.core.enums.NotificationConfigurationStatus;
import com.paytech.platform.support.delivery.core.enums.NotificationDeliveryStatus;
import com.paytech.platform.support.delivery.core.enums.NotificationMedium;
import com.paytech.platform.support.delivery.integration.provider.EmailProviderResult;
import com.paytech.platform.support.test.dal.NotificationTestMapper;
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
    @Autowired java.time.Clock clock;
    @Autowired PlatformTransactionManager transactionManager;

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

    /** 携带 Payload、非正数、非数字或超出 long 范围的任务身份直接终止，不读取业务数据。 */
    @ParameterizedTest
    @MethodSource("invalidEvents")
    void invalidEventIdentityTerminates(String eventKey, String payloadJson) {
        var result = handler.handle(new CompensationEvent(1, eventKey, handler.eventType(), "-", payloadJson, 1));
        assertEquals(CompensationHandlerResult.Outcome.TERMINAL_FAILURE, result.outcome());
        assertEquals("NOTIFICATION_EVENT_INVALID", result.errorCode());
        assertTrue(provider.calls().isEmpty());
    }

    /** 每组只破坏一项任务身份。 */
    static Stream<Arguments> invalidEvents() {
        return Stream.of(Arguments.of("1", "{}"), Arguments.of("0", null), Arguments.of("abc", null),
            Arguments.of("9999999999999999999", null));
    }

    /** 事件工厂拒绝非正数投递标识；执行入口拒绝调用方已有事务。 */
    @Test
    void rejectsInvalidFactoryInputAndCallerTransaction() {
        assertThrows(IllegalArgumentException.class, () -> NotificationDeliveryHandler.buildEvent(0));
        var event = new CompensationEvent(1, "1", handler.eventType(), "-", null, 1);
        assertThrows(ErrorException.class,
            () -> new TransactionTemplate(transactionManager).execute(status -> handler.handle(event)));
    }

    /** 供应商返回的结果码、MessageId 不合法或缺失时一律视为未知，不保存可疑证据。 */
    @ParameterizedTest
    @MethodSource("malformedResults")
    void malformedProviderEvidenceIsUnknown(EmailProviderResult result) {
        provider.expectSendResult("alice@example.com", result);
        processor.process(NotificationRequestBuilder.template());
        fixture.awaitIdle();
        assertEquals(NotificationDeliveryStatus.UNKNOWN.getCode(), mapper.deliveries().getFirst().get("status"));
        assertEquals("RESULT_UNKNOWN", mapper.attempts().getFirst().get("response_code"));
        assertNull(mapper.attempts().getFirst().get("provider_message_id"));
    }

    /** 缺少 Outcome 或结果码、结果码格式错误、MessageId 空白/超长/含控制字符。 */
    static Stream<EmailProviderResult> malformedResults() {
        return Stream.of(new EmailProviderResult(null, null, "CODE"),
            new EmailProviderResult(ACCEPTED, "m-1", null), new EmailProviderResult(ACCEPTED, "m-1", "lower"),
            new EmailProviderResult(ACCEPTED, " ", "ACCEPTED"), new EmailProviderResult(ACCEPTED, "m".repeat(256), "ACCEPTED"),
            new EmailProviderResult(ACCEPTED, "m\u0001", "ACCEPTED"));
    }

    /** 供应商确认无法投递时直接结束为 FAILED，不再重试。 */
    @Test
    void undeliverableFailsWithoutRetry() {
        provider.expectSend("alice@example.com", UNDELIVERABLE);
        processor.process(NotificationRequestBuilder.template());
        fixture.awaitIdle();
        assertEquals(NotificationDeliveryStatus.FAILED.getCode(), mapper.deliveries().getFirst().get("status"));
        assertTrue(mapper.events().isEmpty());
        String id = mapper.deliveries().getFirst().get("delivery_id").toString();
        assertEquals(CompensationHandlerResult.Outcome.SUCCESS,
            handler.handle(new CompensationEvent(1, id, handler.eventType(), "-", null, 2)).outcome());
        assertEquals(1, provider.calls().size());
    }

    /** 支持查询的账号在发送次数用尽后仍为 UNKNOWN 时继续只查询原请求，不终止任务、不重发。 */
    @Test
    void queryCapableUnknownAtAttemptLimitKeepsQuerying() {
        provider.expectSend("alice@example.com", RETRYABLE_REJECTED, RETRYABLE_REJECTED, UNKNOWN);
        processor.process(NotificationRequestBuilder.template());
        fixture.awaitIdle();
        for (int retry = 0; retry < 2; retry++) {
            mapper.retryDue(clock.instant());
            scanDue();
        }
        provider.expectQuery(provider.calls().getLast().getRequestNo(), UNKNOWN);
        scanDue();
        assertEquals(NotificationDeliveryStatus.UNKNOWN.getCode(), mapper.deliveries().getFirst().get("status"));
        assertEquals(3, provider.calls().size());
        assertEquals(1, provider.queryCalls().size());
        assertEquals(1, mapper.events().size());
        assertNotEquals("NOTIFICATION_UNKNOWN_EXHAUSTED", mapper.events().getFirst().get("last_error_code"));
    }

    /** 等待重试期间模板、路由或账号变为不可用时，本地确定失败，不创建新尝试。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("configurationDrift")
    void unusableConfigurationBeforeRetryFails(String scenario, Consumer<NotificationTestMapper> change) {
        provider.expectSend("alice@example.com", RETRYABLE_REJECTED);
        processor.process(NotificationRequestBuilder.template());
        fixture.awaitIdle();
        change.accept(mapper);
        mapper.retryDue(clock.instant());
        scanDue();
        assertEquals(NotificationDeliveryStatus.FAILED.getCode(), mapper.deliveries().getFirst().get("status"));
        assertEquals(1, mapper.attempts().size());
        assertTrue(mapper.events().isEmpty());
    }

    /** 每组只让一项当前配置不可用。 */
    static Stream<Arguments> configurationDrift() {
        String active = NotificationConfigurationStatus.ACTIVE.getCode();
        String disabled = NotificationConfigurationStatus.DISABLED.getCode();
        String email = NotificationMedium.EMAIL.getCode();
        String sms = NotificationMedium.SMS.getCode();
        return Stream.of(
            Arguments.of("模板停用", (Consumer<NotificationTestMapper>) m -> m.changeTemplate(disabled, email)),
            Arguments.of("模板为短信", (Consumer<NotificationTestMapper>) m -> m.changeTemplate(active, sms)),
            Arguments.of("路由停用", (Consumer<NotificationTestMapper>) m -> m.changeRoute(disabled)),
            Arguments.of("路由缺失", (Consumer<NotificationTestMapper>) NotificationTestMapper::clearRoute),
            Arguments.of("语言内容缺失", (Consumer<NotificationTestMapper>) NotificationTestMapper::clearContents),
            Arguments.of("账号停用", (Consumer<NotificationTestMapper>) m -> m.changeProvider(disabled, email)),
            Arguments.of("账号为短信", (Consumer<NotificationTestMapper>) m -> m.changeProvider(active, sms)));
    }
}
