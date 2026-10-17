package com.paytech.platform.support.auto.ext.http.client;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.accesscontrol.domain.clientdeveloper.ClientDeveloperPermissions;
import com.paytech.platform.support.api.clientwebhook.request.ClientChangeWebhookEndpointStatusRequestBody;
import com.paytech.platform.support.api.clientwebhook.request.ClientCreateWebhookEndpointRequestBody;
import com.paytech.platform.support.api.clientwebhook.request.ClientRotateWebhookSecretRequestBody;
import com.paytech.platform.support.api.clientwebhook.request.ClientSaveWebhookEndpointRequestBody;
import com.paytech.platform.support.api.clientwebhook.response.ClientWebhookDeliveryDetailResponseBody;
import com.paytech.platform.support.api.clientwebhook.response.ClientWebhookEndpointsResponseBody;
import com.paytech.platform.support.api.enums.ClientAuthenticationErrorCode;
import com.paytech.platform.support.api.enums.WebhookEventType;
import com.paytech.platform.support.api.webhookmanagement.response.CreateWebhookEndpointResponseBody;
import com.paytech.platform.support.api.webhookmanagement.response.ReplayWebhookDeliveryResponseBody;
import com.paytech.platform.support.api.webhookmanagement.response.RotateWebhookSecretResponseBody;
import com.paytech.platform.support.api.webhookmanagement.response.WebhookDeliverySummaryResponseBody;
import com.paytech.platform.support.api.webhookmanagement.response.WebhookEndpointMutationResponseBody;
import com.paytech.platform.support.api.webhookmanagement.response.WebhookTestResponseBody;
import com.paytech.platform.support.delivery.core.enums.WebhookDeliveryStatus;
import com.paytech.platform.support.delivery.core.enums.WebhookEndpointStatus;
import com.paytech.platform.support.test.base.WebhookTestBase;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.mocker.local.ClientWorkspaceMocker;
import com.paytech.platform.support.test.mocker.local.ClientWorkspaceMocker.Workspace;
import com.paytech.platform.support.test.webhook.WebhookEventBuilder;
import com.paytech.platform.support.test.webhook.WebhookFixture;

import tools.jackson.core.type.TypeReference;

/**
 * 工作区成员自助管理 Webhook：端点维护与一次性密钥、测试事件、投递查询与重发，工作区隔离与客户可见字段。
 *
 * @see com.paytech.platform.support.api.clientwebhook.ClientWebhookApi
 */
class ClientWebhookSelfServiceTest extends WebhookTestBase {

    private static final String BASE = "/client-webhooks/v1";

    private static final String[] ALL = ClientDeveloperPermissions.OWNER_DEFAULTS.toArray(String[]::new);

    @Autowired
    ClientWorkspaceMocker workspaces;

    /** 新建返回一次密钥且不缓存；修改、停用、轮换按版本推进；列表不含密钥；只能看到本工作区。 */
    @Test
    void test_endpointFlow() {
        var owner = workspaces.owner(WebhookFixture.TENANT_ID, ALL);
        fixture.endpoint(WebhookFixture.OTHER_TENANT_ID, fixture.receiverUrl("x"),
            WebhookFixture.eventTypes(WebhookEventType.ORDER_PAID));

        var created = call(owner, HttpMethod.POST, BASE + "/endpoints", create(fixture.receiverUrl("c")))
            .assertStatus(201).assertHeader(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue())
            .as(CreateWebhookEndpointResponseBody.class);

        assertTrue(created.getSigningSecret().matches("whsec_[A-Za-z0-9_-]{43}"));
        assertEquals(1, vault.protectCount());
        var list = call(owner, HttpMethod.GET, BASE + "/endpoints", null).assertOk();
        assertFalse(list.text().contains(created.getSigningSecret()));
        var items = list.as(ClientWebhookEndpointsResponseBody.class).getItems();
        assertEquals(List.of(created.getEndpointId()), items.stream().map(item -> item.getEndpointId()).toList());
        String path = BASE + "/endpoints/" + created.getEndpointId();

        var updated = call(owner, HttpMethod.PUT, path, save(fixture.receiverUrl("d"), 1)).assertOk()
            .as(WebhookEndpointMutationResponseBody.class);
        assertEquals(2, updated.getVersion());
        call(owner, HttpMethod.PUT, path, save(fixture.receiverUrl("d"), 1))
            .assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST);
        var disabled = call(owner, HttpMethod.POST, path + "/status",
            new ClientChangeWebhookEndpointStatusRequestBody(WebhookEndpointStatus.DISABLED.getCode(), 2)).assertOk()
            .as(WebhookEndpointMutationResponseBody.class);
        assertEquals(3, disabled.getVersion());
        var rotated = call(owner, HttpMethod.POST, path + "/secret-rotation", new ClientRotateWebhookSecretRequestBody(false))
            .assertStatus(201).assertHeader(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue())
            .as(RotateWebhookSecretResponseBody.class);
        assertEquals(2, rotated.getRotationNo());
        assertNotEquals(created.getSigningSecret(), rotated.getSigningSecret());
        assertEquals(2, vault.protectCount());
    }

    /** 测试事件发往本工作区端点并返回结果；投递列表、详情只含客户可见字段；重发生成新投递且内容不变。 */
    @Test
    void test_deliveryFlow() {
        var owner = workspaces.owner(WebhookFixture.TENANT_ID, ALL);
        var endpoint = fixture.endpoint(WebhookFixture.TENANT_ID, fixture.receiverUrl("a"),
            WebhookFixture.eventTypes(WebhookEventType.ORDER_PAID));
        receiver.reply("a", 200).reply("a", 200).reply("a", 200);

        var tested = call(owner, HttpMethod.POST, BASE + "/endpoints/" + endpoint.endpointId() + "/test-events", null)
            .assertOk().as(WebhookTestResponseBody.class);
        assertEquals("SUCCEEDED", tested.getResult());
        assertEquals(200, tested.getHttpStatus());

        submit(WebhookFixture.CALLER, WebhookEventBuilder.orderPaid("evt_client_001").build()).assertNoContent(204);
        fixture.awaitIdle();
        var page = call(owner, HttpMethod.GET, BASE + "/deliveries?status=" + WebhookDeliveryStatus.SUCCEEDED.getCode(), null)
            .assertOk().as(new TypeReference<PagingResponseBody<WebhookDeliverySummaryResponseBody>>() {});
        assertEquals(1, page.total());
        var summary = page.items().getFirst();
        assertEquals("evt_client_001", summary.getEventId());

        var detail = call(owner, HttpMethod.GET, BASE + "/deliveries/" + summary.getDeliveryId(), null).assertOk();
        var json = detail.json();
        assertTrue(json.path("requestedBy").isMissingNode());
        assertTrue(json.path("event").path("sourceSystem").isMissingNode());
        var body = detail.as(ClientWebhookDeliveryDetailResponseBody.class);
        assertEquals("evt_client_001", body.getEvent().getEventId());
        assertEquals(1, body.getAttempts().size());
        assertEquals(200, body.getAttempts().getFirst().getHttpStatus());

        var replay = call(owner, HttpMethod.POST, BASE + "/deliveries/" + summary.getDeliveryId() + "/replay", null)
            .assertStatus(201).as(ReplayWebhookDeliveryResponseBody.class);
        fixture.awaitIdle();
        var replayed = webhookData.delivery(Long.parseLong(replay.getDeliveryId()));
        assertEquals(WebhookDeliveryStatus.SUCCEEDED.getCode(), replayed.get("status"));
        assertEquals(owner.accountId(), ((Number) replayed.get("requested_by")).longValue());
        var received = receiver.requests("a");
        assertEquals(new String(received.get(1).body()), new String(received.get(2).body()));
    }

    /** 其他工作区的端点与投递一律 404；账号上下文 403；只读成员不能写。 */
    @Test
    void test_workspaceIsolationAndPermission() {
        var owner = workspaces.owner(WebhookFixture.TENANT_ID, ALL);
        var foreign = fixture.endpoint(WebhookFixture.OTHER_TENANT_ID, fixture.receiverUrl("b"),
            WebhookFixture.eventTypes(WebhookEventType.ORDER_PAID));
        receiver.reply("b", 200);
        submit(WebhookFixture.CALLER, WebhookEventBuilder.orderPaid("evt_other_001").clientId(WebhookFixture.OTHER_CLIENT_ID)
            .build()).assertNoContent(204);
        fixture.awaitIdle();
        long foreignDelivery = ((Number) webhookData.deliveries().getFirst().get("delivery_id")).longValue();
        String foreignPath = BASE + "/endpoints/" + foreign.endpointId();

        call(owner, HttpMethod.PUT, foreignPath, save(fixture.receiverUrl("b"), 1)).assertProblem(404, CommonErrorCode.NOT_FOUND);
        call(owner, HttpMethod.POST, foreignPath + "/status",
            new ClientChangeWebhookEndpointStatusRequestBody(WebhookEndpointStatus.DISABLED.getCode(), 1))
            .assertProblem(404, CommonErrorCode.NOT_FOUND);
        call(owner, HttpMethod.POST, foreignPath + "/secret-rotation", new ClientRotateWebhookSecretRequestBody(true))
            .assertProblem(404, CommonErrorCode.NOT_FOUND);
        call(owner, HttpMethod.POST, foreignPath + "/test-events", null).assertProblem(404, CommonErrorCode.NOT_FOUND);
        call(owner, HttpMethod.GET, BASE + "/deliveries/" + foreignDelivery, null).assertProblem(404, CommonErrorCode.NOT_FOUND);
        call(owner, HttpMethod.POST, BASE + "/deliveries/" + foreignDelivery + "/replay", null)
            .assertProblem(404, CommonErrorCode.NOT_FOUND);
        assertEquals(0, call(owner, HttpMethod.GET, BASE + "/deliveries", null).assertOk()
            .as(new TypeReference<PagingResponseBody<WebhookDeliverySummaryResponseBody>>() {}).total());
        assertEquals(0, vault.protectCount());

        var account = new Workspace(owner.accountId(), workspaces.accountSession(owner.accountId()), owner.tenantId(), 0);
        call(account, HttpMethod.GET, BASE + "/endpoints", null)
            .assertProblem(403, ClientAuthenticationErrorCode.CLIENT_WORKSPACE_UNAVAILABLE);
        var reader = workspaces.owner(WebhookFixture.TENANT_ID, ClientDeveloperPermissions.WEBHOOK_READ);
        call(reader, HttpMethod.GET, BASE + "/endpoints", null).assertOk();
        call(reader, HttpMethod.POST, BASE + "/endpoints", create(fixture.receiverUrl("r")))
            .assertProblem(403, ClientAuthenticationErrorCode.CLIENT_PERMISSION_DENIED);
        call(owner, HttpMethod.GET, BASE + "/deliveries?createdFrom=2026-01-01T00:00:00Z", null).assertStatus(400);
    }

    /** 以工作区会话发送请求。 */
    PlatformResponseChecker call(Workspace workspace, HttpMethod method, String path, Object body) {
        return call(method, path, headers(workspace.actor(), workspace.session()), body);
    }

    /** 订阅 ORDER_PAID 的新建请求；新建不带版本。 */
    static ClientCreateWebhookEndpointRequestBody create(String url) {
        return new ClientCreateWebhookEndpointRequestBody(url, List.of(WebhookEventType.ORDER_PAID.getCode()));
    }

    /** 订阅 ORDER_PAID 的修改请求。 */
    static ClientSaveWebhookEndpointRequestBody save(String url, int version) {
        return new ClientSaveWebhookEndpointRequestBody(url, List.of(WebhookEventType.ORDER_PAID.getCode()), version);
    }
}
