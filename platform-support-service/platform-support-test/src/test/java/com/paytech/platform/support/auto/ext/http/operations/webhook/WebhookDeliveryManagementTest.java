package com.paytech.platform.support.auto.ext.http.operations.webhook;

import com.paytech.platform.support.api.enums.WebhookEventType;
import com.paytech.platform.support.api.enums.OperationsErrorCode;
import com.paytech.platform.support.delivery.core.enums.WebhookDeliveryStatus;
import com.paytech.platform.support.accesscontrol.domain.webhookmanagement.WebhookManagementPermissions;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Result;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.platform.support.api.webhookmanagement.request.ReplayWebhookDeliveryRequestBody;
import com.paytech.platform.support.api.webhookmanagement.response.ReplayWebhookDeliveryResponseBody;
import com.paytech.platform.support.api.webhookmanagement.response.WebhookDeliveryDetailResponseBody;
import com.paytech.platform.support.api.webhookmanagement.response.WebhookDeliverySummaryResponseBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.test.base.WebhookTestBase;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.webhook.WebhookEventBuilder;
import com.paytech.platform.support.test.webhook.WebhookFixture;

import tools.jackson.core.type.TypeReference;

/**
 * 运营查询投递与人工 Replay。
 *
 * @see com.paytech.platform.support.api.webhookmanagement.WebhookManagementApi#deliveries(String, String, String, String, String, Integer, Integer, String, String, String, String)
 * @see com.paytech.platform.support.api.webhookmanagement.WebhookManagementApi#delivery(String, String, String)
 * @see com.paytech.platform.support.api.webhookmanagement.WebhookManagementApi#replayDelivery(String, String, String, ReplayWebhookDeliveryRequestBody)
 */
class WebhookDeliveryManagementTest extends WebhookTestBase {

    private Operator admin;
    private Operator reader;

    /** 管理员持有全部权限；只读人员只有 WEBHOOK_READ。 */
    @BeforeEach
    void setUpOperators() {
        admin = operationsMocker.operator(OperatorBuilder.administrator());
        reader = operationsMocker.operator(OperatorBuilder.administrator()
            .roles(operationsMocker.customRole("WEBHOOK_READER", WebhookManagementPermissions.READ)));
    }

    /** 列表按条件分页，详情含 Envelope 与尝试；Replay 生成新投递，Body 不变并记录操作人。 */
    @Test
    void test_normalFlow() {
        var endpoint = fixture.endpoint(WebhookFixture.TENANT_ID, fixture.receiverUrl("a"), WebhookFixture.eventTypes(WebhookEventType.ORDER_PAID));
        receiver.reply("a", 200).reply("a", 200);
        var event = WebhookEventBuilder.orderPaid("evt_query_001");
        submit(WebhookFixture.CALLER, event.build()).assertNoContent(204);
        fixture.awaitIdle();

        var page = get(deliveriesPath("&eventId=evt_query_001&status=" + WebhookDeliveryStatus.SUCCEEDED.getCode()), reader).assertOk()
            .as(new TypeReference<PagingResponseBody<WebhookDeliverySummaryResponseBody>>() {});
        assertEquals(1, page.total());
        var summary = page.items().getFirst();
        assertEquals("evt_query_001", summary.getEventId());
        assertEquals(Long.toString(endpoint.endpointId()), summary.getEndpointId());
        assertEquals(0, get(deliveriesPath("&status=" + WebhookDeliveryStatus.DEAD.getCode()), reader).assertOk()
            .as(new TypeReference<PagingResponseBody<WebhookDeliverySummaryResponseBody>>() {}).total());

        var detail = get(ADMIN + "/deliveries/" + summary.getDeliveryId(), reader).assertOk()
            .as(WebhookDeliveryDetailResponseBody.class);
        assertEquals("evt_query_001", detail.getEvent().getEnvelope().get("eventId").stringValue());
        assertEquals(WebhookFixture.CALLER, detail.getEvent().getSourceSystem());
        assertEquals(1, detail.getAttempts().size());
        assertEquals(1, detail.getAttempts().getFirst().getRotationNo());
        assertNull(detail.getRequestedBy());

        var replay = post(ADMIN + "/deliveries/" + summary.getDeliveryId() + "/replays", admin,
            Map.of("reason", "Client 要求补发")).assertStatus(201).as(ReplayWebhookDeliveryResponseBody.class);
        fixture.awaitIdle();
        var replayed = webhookData.delivery(Long.parseLong(replay.getDeliveryId()));
        assertEquals(WebhookDeliveryStatus.SUCCEEDED.getCode(), replayed.get("status"));
        assertEquals(Long.parseLong(summary.getDeliveryId()), ((Number) replayed.get("replay_of_delivery_id")).longValue());
        assertEquals(admin.accountId(), ((Number) replayed.get("requested_by")).longValue());
        var received = receiver.requests("a");
        assertEquals(2, received.size());
        assertEquals(new String(received.get(0).body()), new String(received.get(1).body()));
        auditChecker.assertEvent(requestId, "OPERATIONS_WEBHOOK_DELIVERY_REPLAY", Result.SUCCEEDED.getCode(), admin.actor(),
            "WEBHOOK_DELIVERY", summary.getDeliveryId());
    }

    /** 进行中的投递不能 Replay；查询时间范围超过 31 天或缺失返回 400；Replay 需要 WEBHOOK_MANAGE。 */
    @Test
    void test_businessRuleViolation() {
        fixture.endpoint(WebhookFixture.TENANT_ID, fixture.receiverUrl("a"), WebhookFixture.eventTypes(WebhookEventType.ORDER_PAID));
        receiver.reply("a", 500);
        submit(WebhookFixture.CALLER, WebhookEventBuilder.orderPaid("evt_wait_001").build()).assertNoContent(204);
        fixture.awaitIdle();
        String deliveryId = webhookData.deliveries().getFirst().get("delivery_id").toString();

        post(ADMIN + "/deliveries/" + deliveryId + "/replays", reader, Map.of("reason", "x"))
            .assertProblem(403, OperationsErrorCode.OPERATIONS_PERMISSION_DENIED);
        post(ADMIN + "/deliveries/" + deliveryId + "/replays", admin, Map.of("reason", "x"))
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        get(ADMIN + "/deliveries?clientId=" + WebhookFixture.CLIENT_ID + "&createdFrom=2026-01-01T00:00:00Z"
            + "&createdTo=2026-03-01T00:00:00Z", reader).assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        get(ADMIN + "/deliveries/880000000000079999", reader).assertProblem(404, CommonErrorCode.NOT_FOUND);
        assertEquals(1, webhookData.deliveries().size());
    }

    /** 以测试时钟为中心的一天查询范围。 */
    String deliveriesPath(String extra) {
        var now = clock.instant();
        return ADMIN + "/deliveries?clientId=" + WebhookFixture.CLIENT_ID + "&createdFrom="
            + now.minus(Duration.ofHours(12)) + "&createdTo=" + now.plus(Duration.ofHours(12)) + "&page=1&size=10" + extra;
    }
}
