package com.paytech.platform.support.auto.ext.http.webhook;

import com.paytech.platform.support.api.enums.WebhookEventType;
import com.paytech.platform.support.delivery.core.enums.WebhookDeliveryStatus;
import org.springframework.http.HttpMethod;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.platform.support.api.webhook.request.SubmitWebhookEventRequestBody;
import com.paytech.platform.support.test.base.WebhookTestBase;
import com.paytech.platform.support.test.webhook.WebhookEventBuilder;
import com.paytech.platform.support.test.webhook.WebhookFixture;
import com.paytech.platform.support.test.webhook.WebhookSignatures;

/**
 * 内部事件受理：调用方白名单、Envelope 与摘要校验、按订阅分发、重复与冲突、首次投递。
 *
 * @see com.paytech.platform.support.api.webhook.WebhookEventApi#submitEvent(String, SubmitWebhookEventRequestBody)
 */
class WebhookEventApiTest extends WebhookTestBase {

    private static final String SUBSCRIBED = WebhookFixture.eventTypes(WebhookEventType.ORDER_PAID,
        WebhookEventType.REFUND_SUCCEEDED);

    /** 只投递给本租户订阅了该类型的 ACTIVE 端点；Body 为规范字节、签名可用当前密钥验证。 */
    @Test
    void test_normalFlow() {
        var target = fixture.endpoint(WebhookFixture.TENANT_ID, fixture.receiverUrl("a"), SUBSCRIBED);
        fixture.endpoint(WebhookFixture.TENANT_ID, fixture.receiverUrl("unsubscribed"), WebhookFixture.eventTypes(WebhookEventType.REFUND_SUCCEEDED));
        fixture.endpoint(WebhookFixture.OTHER_TENANT_ID, fixture.receiverUrl("other"), SUBSCRIBED);
        receiver.reply("a", 200);
        var event = WebhookEventBuilder.orderPaid("evt_normal_001");

        submit(WebhookFixture.CALLER, event.build()).assertNoContent(204);
        fixture.awaitIdle();

        var events = webhookData.events();
        assertEquals(1, events.size());
        assertEquals("evt_normal_001", events.getFirst().get("source_event_key"));
        assertEquals(WebhookFixture.CALLER, events.getFirst().get("source_system"));
        var deliveries = webhookData.deliveries();
        assertEquals(1, deliveries.size());
        assertEquals(target.endpointId(), ((Number) deliveries.getFirst().get("endpoint_id")).longValue());
        assertEquals(WebhookDeliveryStatus.SUCCEEDED.getCode(), deliveries.getFirst().get("status"));
        assertEquals(0, webhookData.tasks().size(), "成功后补偿任务完成");

        var received = receiver.requests("a");
        assertEquals(1, received.size());
        assertEquals(HttpMethod.POST.name(), received.getFirst().method());
        assertTrue(received.getFirst().contentType().startsWith("application/json"));
        assertEquals("Code-MoR-Webhook/1", received.getFirst().userAgent());
        assertArrayEquals(event.canonicalEnvelope(), received.getFirst().body());
        var signature = WebhookSignatures.parse(received.getFirst().signature());
        assertEquals(clock.instant().getEpochSecond(), signature.timestamp(), "t 为本次发送时刻");
        assertEquals(1, signature.signatures().size(), "没有宽限密钥时只带一个签名");
        assertEquals(WebhookSignatures.expected(target.secret(), signature.timestamp(), received.getFirst().body()),
            signature.signatures().getFirst());
    }

    /** 没有订阅也受理并保存事件，不生成投递和任务。 */
    @Test
    void test_noSubscription() {
        submit(WebhookFixture.CALLER, WebhookEventBuilder.orderPaid("evt_nosub_001").build()).assertNoContent(204);

        assertEquals(1, webhookData.events().size());
        assertEquals(0, webhookData.deliveries().size());
        assertEquals(0, webhookData.tasks().size());
    }

    /** 同一调用方同一 eventId 内容相同：仍返回 204，不新增事件或投递。 */
    @Test
    void test_duplicateSameContent() {
        fixture.endpoint(WebhookFixture.TENANT_ID, fixture.receiverUrl("a"), SUBSCRIBED);
        receiver.reply("a", 200);
        var event = WebhookEventBuilder.orderPaid("evt_dup_001");

        submit(WebhookFixture.CALLER, event.build()).assertNoContent(204);
        fixture.awaitIdle();
        submit(WebhookFixture.CALLER, event.build()).assertNoContent(204);

        assertEquals(1, webhookData.events().size());
        assertEquals(1, webhookData.deliveries().size());
        assertEquals(1, receiver.requests("a").size());
    }

    /** 同一 eventId 改了内容或来源版本：409，不写库。 */
    @Test
    void test_duplicateDifferentContent() {
        submit(WebhookFixture.CALLER, WebhookEventBuilder.orderPaid("evt_conflict_001").build()).assertNoContent(204);

        submit(WebhookFixture.CALLER, WebhookEventBuilder.orderPaid("evt_conflict_001").objectField("orderStatus",
            "CANCELLED").build()).assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST);
        submit(WebhookFixture.CALLER, WebhookEventBuilder.orderPaid("evt_conflict_001").sourceVersion(6).build())
            .assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST);

        assertEquals(1, webhookData.events().size());
    }

    /** 不同调用方使用相同 eventId 互不影响。 */
    @Test
    void test_sameEventIdFromAnotherCaller() {
        submit(WebhookFixture.CALLER, WebhookEventBuilder.orderPaid("evt_shared_001").build()).assertNoContent(204);
        submit(WebhookFixture.PEER_CALLER, WebhookEventBuilder.orderPaid("evt_shared_001").build()).assertNoContent(204);

        assertEquals(2, webhookData.events().size());
    }

    /** 未登记的调用方与越权事件类型返回同一业务错误，不写库。 */
    @Test
    void test_callerNotAllowed() {
        submit("unknown-service", WebhookEventBuilder.orderPaid("evt_caller_001").build())
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        submit(WebhookFixture.PEER_CALLER, WebhookEventBuilder.orderPaid("evt_caller_002")
            .eventType(WebhookEventType.REFUND_SUCCEEDED).build()).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);

        assertEquals(0, webhookData.events().size());
    }

    /** Envelope 多出顶层字段、摘要与内容不符：400；摘要格式错误：422；均不写库。 */
    @Test
    void test_invalidParameter() {
        submit(WebhookFixture.CALLER, WebhookEventBuilder.orderPaid("evt_invalid_001").extraTopLevel("extra", "x")
            .build()).assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        submit(WebhookFixture.CALLER, WebhookEventBuilder.orderPaid("evt_invalid_002").payloadHash("0".repeat(64))
            .build()).assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        submit(WebhookFixture.CALLER, WebhookEventBuilder.orderPaid("evt_invalid_003").payloadHash("XYZ").build())
            .assertProblem(422, CommonErrorCode.VALIDATION_ERROR);

        assertEquals(0, webhookData.events().size());
    }

    /** Client 未开通平台访问：业务错误，不写库。 */
    @Test
    void test_noDataFound() {
        submit(WebhookFixture.CALLER, WebhookEventBuilder.orderPaid("evt_client_001")
            .clientId(WebhookFixture.UNKNOWN_CLIENT_ID).build()).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);

        assertEquals(0, webhookData.events().size());
    }
}
