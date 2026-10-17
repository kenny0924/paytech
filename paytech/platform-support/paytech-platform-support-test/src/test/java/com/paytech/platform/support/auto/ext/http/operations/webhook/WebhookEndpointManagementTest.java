package com.paytech.platform.support.auto.ext.http.operations.webhook;

import com.paytech.platform.support.api.enums.WebhookEndpointErrorCode;
import com.paytech.platform.support.api.enums.WebhookEventType;
import com.paytech.platform.support.api.enums.OperationsErrorCode;
import com.paytech.platform.support.delivery.core.enums.WebhookEndpointStatus;
import com.paytech.platform.support.delivery.core.enums.WebhookSecretStatus;
import com.paytech.platform.support.delivery.core.enums.WebhookAttemptStatus;
import com.paytech.platform.support.delivery.core.enums.WebhookFailureCode;
import com.paytech.platform.support.accesscontrol.domain.webhookmanagement.WebhookManagementPermissions;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Result;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.platform.support.api.webhookmanagement.request.ChangeWebhookEndpointStatusRequestBody;
import com.paytech.platform.support.api.webhookmanagement.request.RotateWebhookSecretRequestBody;
import com.paytech.platform.support.api.webhookmanagement.request.SaveWebhookEndpointRequestBody;
import com.paytech.platform.support.api.webhookmanagement.response.CreateWebhookEndpointResponseBody;
import com.paytech.platform.support.api.webhookmanagement.response.RotateWebhookSecretResponseBody;
import com.paytech.platform.support.api.webhookmanagement.response.WebhookEndpointMutationResponseBody;
import com.paytech.platform.support.api.webhookmanagement.response.WebhookEndpointResponseBody;
import com.paytech.platform.support.api.webhookmanagement.response.WebhookTestResponseBody;
import com.paytech.platform.support.test.base.WebhookTestBase;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.webhook.WebhookFixture;
import com.paytech.platform.support.test.webhook.WebhookSignatures;

import tools.jackson.core.type.TypeReference;

/**
 * 运营维护端点、订阅与签名密钥，发送测试事件。
 *
 * @see com.paytech.platform.support.api.webhookmanagement.WebhookManagementApi#endpoints(String, String, String)
 * @see com.paytech.platform.support.api.webhookmanagement.WebhookManagementApi#createEndpoint(String, String, String, SaveWebhookEndpointRequestBody)
 * @see com.paytech.platform.support.api.webhookmanagement.WebhookManagementApi#updateEndpoint(String, String, String, SaveWebhookEndpointRequestBody)
 * @see com.paytech.platform.support.api.webhookmanagement.WebhookManagementApi#changeEndpointStatus(String, String, String, ChangeWebhookEndpointStatusRequestBody)
 * @see com.paytech.platform.support.api.webhookmanagement.WebhookManagementApi#rotateSecret(String, String, String, RotateWebhookSecretRequestBody)
 * @see com.paytech.platform.support.api.webhookmanagement.WebhookManagementApi#testEndpoint(String, String, String)
 */
class WebhookEndpointManagementTest extends WebhookTestBase {

    private static final String CLIENT_ENDPOINTS = ADMIN + "/clients/" + WebhookFixture.CLIENT_ID + "/endpoints";

    private Operator admin;
    private Operator reader;
    private Operator viewer;

    /** 管理员持有全部权限；只读人员只有 WEBHOOK_READ；运营只读人员没有 Webhook 权限。 */
    @BeforeEach
    void setUpOperators() {
        admin = operationsMocker.operator(OperatorBuilder.administrator());
        reader = operationsMocker.operator(OperatorBuilder.administrator()
            .roles(operationsMocker.customRole("WEBHOOK_READER", WebhookManagementPermissions.READ)));
        viewer = operationsMocker.operator(OperatorBuilder.viewer());
    }

    /** 新建返回一次密钥明文并存入 Vault；修改、停用按版本推进；列表不含密钥。 */
    @Test
    void test_normalFlow() {
        var created = post(CLIENT_ENDPOINTS, admin, save(fixture.receiverUrl("a"), List.of(WebhookEventType.ORDER_PAID.getCode()), 0))
            .assertStatus(201).assertHeader(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue()).as(CreateWebhookEndpointResponseBody.class);
        assertTrue(created.getSigningSecret().matches("whsec_[A-Za-z0-9_-]{43}"));
        assertEquals(1, vault.protectCount());
        var secrets = webhookData.secrets(Long.parseLong(created.getEndpointId()));
        assertEquals(1, secrets.size());
        assertEquals(created.getSigningSecret(), vault.secret((String) secrets.getFirst().get("secret_vault_token")));
        auditChecker.assertEvent(requestId, "OPERATIONS_WEBHOOK_ENDPOINT_CREATE", Result.SUCCEEDED.getCode(), admin.actor(),
            "WEBHOOK_ENDPOINT", created.getEndpointId());

        String path = ADMIN + "/endpoints/" + created.getEndpointId();
        var updated = put(path, admin, save(fixture.receiverUrl("b"), List.of(WebhookEventType.ORDER_PAID.getCode(), WebhookEventType.REFUND_SUCCEEDED.getCode()), 1))
            .assertOk().as(WebhookEndpointMutationResponseBody.class);
        assertEquals(2, updated.getVersion());
        var disabled = patch(path + "/status", admin, status(WebhookEndpointStatus.DISABLED, 2)).assertOk()
            .as(WebhookEndpointMutationResponseBody.class);
        assertEquals(3, disabled.getVersion());

        var listed = get(CLIENT_ENDPOINTS, reader).assertOk().as(new TypeReference<List<WebhookEndpointResponseBody>>() {});
        assertEquals(1, listed.size());
        assertEquals(fixture.receiverUrl("b"), listed.getFirst().getUrl());
        assertEquals(List.of(WebhookEventType.ORDER_PAID.getCode(), WebhookEventType.REFUND_SUCCEEDED.getCode()), listed.getFirst().getEventTypes());
        assertEquals(WebhookEndpointStatus.DISABLED.getCode(), listed.getFirst().getStatus());
        assertEquals(1, listed.getFirst().getSigningSecret().getRotationNo());
        assertNull(listed.getFirst().getSigningSecret().getPreviousValidUntil());
    }

    /** 读需要 WEBHOOK_READ，写需要 WEBHOOK_MANAGE；无权限时不调用 Vault。 */
    @Test
    void test_permission() {
        get(CLIENT_ENDPOINTS, viewer).assertProblem(403, OperationsErrorCode.OPERATIONS_PERMISSION_DENIED);
        post(CLIENT_ENDPOINTS, reader, save(fixture.receiverUrl("a"), List.of(WebhookEventType.ORDER_PAID.getCode()), 0))
            .assertProblem(403, OperationsErrorCode.OPERATIONS_PERMISSION_DENIED);

        assertEquals(0, vault.protectCount());
        assertEquals(0, webhookData.endpoints().size());
    }

    /** 地址与订阅不合法：不调用 Vault、不写库。 */
    @Test
    void test_invalidParameter() {
        post(CLIENT_ENDPOINTS, admin, save("ftp://hooks.example.com/a", List.of(WebhookEventType.ORDER_PAID.getCode()), 0))
            .assertProblem(400, WebhookEndpointErrorCode.WEBHOOK_URL_NOT_ALLOWED);
        post(CLIENT_ENDPOINTS, admin, save("https://user:pass@hooks.example.com/a", List.of(WebhookEventType.ORDER_PAID.getCode()), 0))
            .assertProblem(400, WebhookEndpointErrorCode.WEBHOOK_URL_NOT_ALLOWED);
        post(CLIENT_ENDPOINTS, admin, save(fixture.receiverUrl("a"), List.of(WebhookEventType.WEBHOOK_PING.getCode()), 0))
            .assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        post(CLIENT_ENDPOINTS, admin, save(fixture.receiverUrl("a"), List.of(WebhookEventType.ORDER_PAID.getCode(), WebhookEventType.ORDER_PAID.getCode()), 0))
            .assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        post(CLIENT_ENDPOINTS, admin, save(fixture.receiverUrl("a"), List.of(), 0))
            .assertProblem(422, CommonErrorCode.VALIDATION_ERROR);

        assertEquals(0, vault.protectCount());
        assertEquals(0, webhookData.endpoints().size());
    }

    /** 地址解析到内网或无法解析：专用错误码；Client 未开通、端点数达到上限：业务错误。 */
    @Test
    void test_businessRuleViolation() {
        post(CLIENT_ENDPOINTS, admin, save("http://10.0.0.1/hook", List.of(WebhookEventType.ORDER_PAID.getCode()), 0))
            .assertProblem(400, WebhookEndpointErrorCode.WEBHOOK_URL_NOT_PUBLIC);
        post(CLIENT_ENDPOINTS, admin, save("https://hooks.code-mor.invalid/a", List.of(WebhookEventType.ORDER_PAID.getCode()), 0))
            .assertProblem(400, WebhookEndpointErrorCode.WEBHOOK_URL_UNRESOLVABLE);
        post(ADMIN + "/clients/" + WebhookFixture.UNKNOWN_CLIENT_ID + "/endpoints", admin,
            save(fixture.receiverUrl("a"), List.of(WebhookEventType.ORDER_PAID.getCode()), 0)).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        assertEquals(0, vault.protectCount());

        for (int index = 0; index < 5; index++) {
            fixture.endpoint(WebhookFixture.TENANT_ID, fixture.receiverUrl("e" + index), WebhookFixture.eventTypes(WebhookEventType.ORDER_PAID));
        }
        post(CLIENT_ENDPOINTS, admin, save(fixture.receiverUrl("a"), List.of(WebhookEventType.ORDER_PAID.getCode()), 0))
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        assertEquals(5, webhookData.endpoints().size());
    }

    /** 版本不符 409；修改为内网地址 400 专用错误码；状态或内容没有变化 400；端点不存在 404。 */
    @Test
    void test_conflictAndNotFound() {
        var endpoint = fixture.endpoint(WebhookFixture.TENANT_ID, fixture.receiverUrl("a"), WebhookFixture.eventTypes(WebhookEventType.ORDER_PAID));
        String path = ADMIN + "/endpoints/" + endpoint.endpointId();

        put(path, admin, save(fixture.receiverUrl("b"), List.of(WebhookEventType.ORDER_PAID.getCode()), 7))
            .assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST);
        put(path, admin, save("http://10.0.0.1/hook", List.of(WebhookEventType.ORDER_PAID.getCode()), 1))
            .assertProblem(400, WebhookEndpointErrorCode.WEBHOOK_URL_NOT_PUBLIC);
        put(path, admin, save(fixture.receiverUrl("a"), List.of(WebhookEventType.ORDER_PAID.getCode()), 1))
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        patch(path + "/status", admin, status(WebhookEndpointStatus.ACTIVE, 1)).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        patch(ADMIN + "/endpoints/880000000000079999/status", admin, status(WebhookEndpointStatus.DISABLED, 1))
            .assertProblem(404, CommonErrorCode.NOT_FOUND);
    }

    /** 宽限轮换：旧密钥 1 小时内仍签名；立即作废：此前的密钥都不再签名。测试事件按当前密钥签名且不落库。 */
    @Test
    void test_rotateAndTestEndpoint() {
        var endpoint = fixture.endpoint(WebhookFixture.TENANT_ID, fixture.receiverUrl("a"), WebhookFixture.eventTypes(WebhookEventType.ORDER_PAID));
        String path = ADMIN + "/endpoints/" + endpoint.endpointId();

        var graced = post(path + "/signing-secret/rotations", admin, rotate(false)).assertOk()
            .assertHeader(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue()).as(RotateWebhookSecretResponseBody.class);
        assertEquals(2, graced.getRotationNo());
        assertEquals(clock.instant().plus(Duration.ofHours(1)).toString(), graced.getPreviousValidUntil());
        receiver.reply("a", 200);
        var tested = post(path + "/tests", admin, null).assertOk().as(WebhookTestResponseBody.class);
        assertEquals(WebhookAttemptStatus.SUCCEEDED.getCode(), tested.getResult());
        assertEquals(200, tested.getHttpStatus());
        assertSignedBy(receiver.requests("a").getLast(), graced.getSigningSecret(), endpoint.secret());
        auditChecker.assertEvent(requestId, "OPERATIONS_WEBHOOK_ENDPOINT_TEST", Result.SUCCEEDED.getCode(), admin.actor(),
            "WEBHOOK_ENDPOINT", Long.toString(endpoint.endpointId()));

        var immediate = post(path + "/signing-secret/rotations", admin, rotate(true)).assertOk()
            .as(RotateWebhookSecretResponseBody.class);
        assertEquals(3, immediate.getRotationNo());
        assertNull(immediate.getPreviousValidUntil());
        receiver.reply("a", 500);
        tested = post(path + "/tests", admin, null).assertOk().as(WebhookTestResponseBody.class);
        assertEquals(WebhookAttemptStatus.FAILED.getCode(), tested.getResult());
        assertEquals(WebhookFailureCode.HTTP_STATUS.getCode(), tested.getFailureCode());
        assertSignedBy(receiver.requests("a").getLast(), immediate.getSigningSecret());

        var statuses = webhookData.secrets(endpoint.endpointId()).stream().map(row -> row.get("status")).toList();
        assertEquals(3, statuses.size());
        assertEquals(1, statuses.stream().filter(WebhookSecretStatus.ACTIVE.getCode()::equals).count());
        assertEquals(0, webhookData.events().size());
        assertEquals(0, webhookData.deliveries().size());
    }

    /** 接收端收到的签名恰好由给定密钥依次生成。 */
    static void assertSignedBy(com.paytech.platform.support.test.mocker.external.WebhookReceiverMocker.Received request,
        String... secrets) {
        var signature = WebhookSignatures.parse(request.signature());
        assertEquals(java.util.Arrays.stream(secrets)
            .map(secret -> WebhookSignatures.expected(secret, signature.timestamp(), request.body())).toList(),
            signature.signatures());
    }

    /** 新建或修改请求体。 */
    static Map<String, Object> save(String url, List<String> eventTypes, int expectedVersion) {
        return Map.of("url", url, "eventTypes", eventTypes, "expectedVersion", expectedVersion, "reason", "测试维护");
    }

    /** 启停请求体。 */
    static Map<String, Object> status(WebhookEndpointStatus status, int expectedVersion) {
        return Map.of("status", status.getCode(), "expectedVersion", expectedVersion, "reason", "测试启停");
    }

    /** 轮换请求体。 */
    static Map<String, Object> rotate(boolean immediate) {
        return Map.of("immediate", immediate, "reason", "测试轮换");
    }
}
