package com.paytech.platform.support.auto.ext.http.operations.notification;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.notificationmanagement.NotificationManagementApi;
import com.paytech.platform.support.api.notificationmanagement.request.ChangeNotificationStatusRequestBody;
import com.paytech.platform.support.api.notificationmanagement.request.CreateNotificationProviderRequestBody;
import com.paytech.platform.support.api.notificationmanagement.request.ReplaceNotificationCredentialRequestBody;
import com.paytech.platform.support.api.notificationmanagement.request.TestSendNotificationProviderRequestBody;
import com.paytech.platform.support.api.notificationmanagement.request.UpdateNotificationProviderRequestBody;
import com.paytech.platform.support.api.notificationmanagement.response.NotificationMutationResponseBody;
import com.paytech.platform.support.api.notificationmanagement.response.NotificationProviderResponseBody;
import com.paytech.platform.support.api.notificationmanagement.response.NotificationProviderTestSendResponseBody;
import com.paytech.platform.support.delivery.core.constant.NotificationProviderTypes;
import com.paytech.platform.support.delivery.core.enums.NotificationConfigurationStatus;
import com.paytech.platform.support.test.base.NotificationManagementTestBase;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.mocker.external.SesMocker;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;

import tools.jackson.core.type.TypeReference;

/**
 * 发送账号维护：新建、编辑、录入或更换密钥、测试发送，以及启用时的配置完整性检查。
 * 密钥只经 Vault Mock 的 Protect 合同写入，SES 请求经真实客户端发往 SES Mock；密钥与 Token 不进入响应、审计和日志。
 *
 * @see NotificationManagementApi#createProvider(String, String, CreateNotificationProviderRequestBody)
 * @see NotificationManagementApi#updateProvider(String, String, String, UpdateNotificationProviderRequestBody)
 * @see NotificationManagementApi#replaceProviderCredential(String, String, String,
 *      ReplaceNotificationCredentialRequestBody)
 * @see NotificationManagementApi#testSendProvider(String, String, String, TestSendNotificationProviderRequestBody)
 * @see NotificationManagementApi#changeProviderStatus(String, String, String, ChangeNotificationStatusRequestBody)
 */
@ExtendWith(OutputCaptureExtension.class)
class NotificationProviderMaintenanceTest extends NotificationManagementTestBase {

    /** 管理接口新建的账号编码；以 NOTIFICATION_ADMIN_ 开头，由 TestMapper 清理。 */
    static final String NEW_PROVIDER = "NOTIFICATION_ADMIN_SES_BACKUP";

    /** NotificationTestMapper.sesProvider 写入的 SES 测试账号。 */
    static final String SES_PROVIDER = "NOTIFICATION_TEST_SES";

    /** 合成的 SQS 事件队列 URL。 */
    static final String EVENT_QUEUE = "https://sqs.ap-southeast-1.amazonaws.com/123456789012/notification-events";

    /** SES 测试账号的合成密钥 Token。 */
    static final String SES_TOKEN = "vlt_sesCredentialTokenForLoopbackMock000000";

    /** Vault Mock 为新密钥返回的合成 Token。 */
    static final String NEW_TOKEN = "vlt_newProviderCredentialTokenForLoopback00";

    /** 页面录入的合成密钥；Secret 用于验证不进入响应、审计与日志。 */
    static final String ACCESS_KEY = "AKIANEWSYNTHETIC0001";

    static final String SECRET_MARKER = "NEW-SECRET-MARKER/abc+123";

    /** Vault 中 SES 测试账号的合成密钥对。 */
    static final String SES_ACCESS_KEY = "AKIAVAULTSYNTHETIC01";

    static final String SES_CREDENTIAL =
        "{\"accessKeyId\":\"" + SES_ACCESS_KEY + "\",\"secretAccessKey\":\"VAULT-SECRET-MARKER\"}";

    @Autowired SesMocker ses;

    /** 只有维护权限、没有密钥权限的人员。 */
    Operator manager;

    /** 本类公共配置：只有维护权限的人员。 */
    @BeforeEach
    void setUpProviderMaintenanceMocks() {
        manager = operationsMocker.operator(OperatorBuilder.administrator()
            .roles(operationsMocker.customRole("NOTIFICATION_MANAGER", "NOTIFICATION_READ", "NOTIFICATION_MANAGE")));
    }

    /** 新建为停用、发件地址规范化、空 Configuration Set 视为未配置，并记审计；列表显示缺少密钥。 */
    @Test
    void test_createProvider() {
        var created = post(BASE + "/providers", manager, create(NEW_PROVIDER, " Ops@Example.COM ", ""))
            .assertOk().as(NotificationMutationResponseBody.class);

        assertEquals(NEW_PROVIDER, created.getCode());
        assertEquals(1, created.getVersion());
        var row = mapper.providerState(NEW_PROVIDER);
        assertAll(() -> assertEquals(NotificationConfigurationStatus.DISABLED.getCode(), row.get("status")),
            () -> assertEquals(NotificationProviderTypes.AMAZON_SES, row.get("provider_type")),
            () -> assertEquals("eu-west-1", row.get("region")),
            () -> assertEquals("ops@example.com", row.get("from_address")),
            () -> assertNull(row.get("configuration_set")), () -> assertNull(row.get("credential_vault_token")));
        auditChecker.assertEvent(requestId, "OPERATIONS_NOTIFICATION_PROVIDER_CREATE", "SUCCEEDED",
            manager.actor(), "NOTIFICATION_PROVIDER", NEW_PROVIDER);
        var item = provider(NEW_PROVIDER);
        assertAll(() -> assertEquals(List.of("credential"), item.getMissingConfiguration()),
            () -> assertFalse(item.isCredentialConfigured()), () -> assertTrue(item.isAdapterConfigured()));
    }

    /** 编码重复 400、非法区域与类型 422、发件地址不合法 400、只读人员 403。 */
    @Test
    void test_createProviderRejected() {
        post(BASE + "/providers", manager, create(SES_PROVIDER, "ops@example.com", null))
            .assertProblem(400, "BUSINESS_CHECK_FAILED");
        var badRegion = create(NEW_PROVIDER, "ops@example.com", null);
        badRegion.put("region", "Middle East");
        post(BASE + "/providers", manager, badRegion).assertProblem(422, "VALIDATION_ERROR").assertFieldError("region");
        var badType = create(NEW_PROVIDER, "ops@example.com", null);
        badType.put("providerType", "SMTP");
        post(BASE + "/providers", manager, badType).assertProblem(422, "VALIDATION_ERROR")
            .assertFieldError("providerType");
        post(BASE + "/providers", manager, create(NEW_PROVIDER, "Ops <ops@example.com>", null))
            .assertProblem(400, "INVALID_REQUEST");
        post(BASE + "/providers", reader, create(NEW_PROVIDER, "ops@example.com", null))
            .assertProblem(403, "OPERATIONS_PERMISSION_DENIED");
        assertNull(mapper.providerState(NEW_PROVIDER));
    }

    /** 编辑区域、发件地址与 Configuration Set 并记审计；修改立即作用于测试发送；版本过期 409，不存在 404。 */
    @Test
    void test_updateProvider() {
        String path = BASE + "/providers/" + SES_PROVIDER;
        var updated = put(path, manager, update("eu-west-1", "ops@example.com", "prod-events", 1)).assertOk()
            .as(NotificationMutationResponseBody.class);

        assertEquals(2, updated.getVersion());
        var row = mapper.providerState(SES_PROVIDER);
        assertAll(() -> assertEquals("eu-west-1", row.get("region")),
            () -> assertEquals("ops@example.com", row.get("from_address")),
            () -> assertEquals("prod-events", row.get("configuration_set")),
            () -> assertEquals(SES_TOKEN, row.get("credential_vault_token")));
        auditChecker.assertEvent(requestId, "OPERATIONS_NOTIFICATION_PROVIDER_UPDATE", "SUCCEEDED",
            manager.actor(), "NOTIFICATION_PROVIDER", SES_PROVIDER);
        put(path, manager, update("eu-west-1", "ops@example.com", null, 1)).assertProblem(409, "CONCURRENT_REQUEST");
        put(BASE + "/providers/NOTIFICATION_ADMIN_NONE", manager, update("eu-west-1", "ops@example.com", null, 1))
            .assertProblem(404, "NOT_FOUND");
        put(path, reader, update("eu-west-1", "ops@example.com", null, 2))
            .assertProblem(403, "OPERATIONS_PERMISSION_DENIED");

        vault.allowCredential(SES_TOKEN, SES_CREDENTIAL);
        ses.accept("ops@example.com", "ses-test-1");
        post(path + "/test-send", admin, testSend("ops@example.com")).assertOk();
        var request = ses.requests().getFirst();
        assertAll(() -> assertEquals("ops@example.com", request.from()),
            () -> assertEquals("prod-events", request.body().path("ConfigurationSetName").asString()),
            () -> assertTrue(request.headers().get("authorization").contains("/eu-west-1/ses/aws4_request")));
    }

    /** 事件队列 URL：新建时可填，编辑可改可清空并推进版本；格式不符 422；列表返回 URL 与拉取运行状态。 */
    @Test
    void test_eventQueueUrl() {
        var body = create(NEW_PROVIDER, "ops@example.com", null);
        body.put("eventQueueUrl", EVENT_QUEUE);
        post(BASE + "/providers", manager, body).assertOk();
        assertEquals(EVENT_QUEUE, mapper.providerState(NEW_PROVIDER).get("event_queue_url"));
        auditChecker.assertEvent(requestId, "OPERATIONS_NOTIFICATION_PROVIDER_CREATE", "SUCCEEDED",
            manager.actor(), "NOTIFICATION_PROVIDER", NEW_PROVIDER);

        String path = BASE + "/providers/" + SES_PROVIDER;
        var change = update("me-central-1", "noreply@example.com", null, 1);
        change.put("eventQueueUrl", EVENT_QUEUE);
        assertEquals(2, put(path, manager, change).assertOk().as(NotificationMutationResponseBody.class).getVersion());
        assertEquals(EVENT_QUEUE, mapper.providerState(SES_PROVIDER).get("event_queue_url"));

        mapper.providerEventFailure(SES_PROVIDER, "SQS_ACCESS_DENIED");
        var item = provider(SES_PROVIDER);
        assertAll(() -> assertEquals(EVENT_QUEUE, item.getEventQueueUrl()),
            () -> assertEquals("SQS_ACCESS_DENIED", item.getEventPollError()),
            () -> assertNotNull(item.getEventReceiveTime()), () -> assertNull(item.getEventPollTime()),
            () -> assertEquals(2, item.getVersion(), "运行状态不推进版本"));

        var cleared = update("me-central-1", "noreply@example.com", null, 2);
        cleared.put("eventQueueUrl", "");
        put(path, manager, cleared).assertOk();
        assertNull(mapper.providerState(SES_PROVIDER).get("event_queue_url"), "空串视为清空");

        for (String invalid : List.of("http://sqs.ap-southeast-1.amazonaws.com/123456789012/q",
            "https://sqs.ap-southeast-1.example.com/123456789012/q", "https://sqs.Asia.amazonaws.com/123456789012/q",
            "https://sqs.ap-southeast-1.amazonaws.com/1234/q")) {
            var bad = update("me-central-1", "noreply@example.com", null, 3);
            bad.put("eventQueueUrl", invalid);
            put(path, manager, bad).assertProblem(422, "VALIDATION_ERROR").assertFieldError("eventQueueUrl");
        }
    }

    /** 录入密钥：经 Vault Protect 写入 Token 并记审计；响应、审计、日志都不含密钥与 Token。 */
    @Test
    void test_replaceCredential(CapturedOutput output) {
        post(BASE + "/providers", manager, create(NEW_PROVIDER, "ops@example.com", null)).assertOk();
        vault.expectCredentialProtect(NEW_TOKEN);

        var response = put(BASE + "/providers/" + NEW_PROVIDER + "/credential", admin, credential(1)).assertOk();

        assertEquals(2, response.as(NotificationMutationResponseBody.class).getVersion());
        var row = mapper.providerState(NEW_PROVIDER);
        assertAll(() -> assertEquals(NEW_TOKEN, row.get("credential_vault_token")),
            () -> assertNotNull(row.get("credential_update_time")),
            () -> assertEquals(List.of("{\"accessKeyId\":\"" + ACCESS_KEY + "\",\"secretAccessKey\":\""
                + SECRET_MARKER + "\"}"), vault.protectedCredentials()),
            () -> assertTrue(vault.credentialProtectKeys().getFirst().startsWith("notification-provider-credential.")));
        var event = auditChecker.assertEvent(requestId, "OPERATIONS_NOTIFICATION_PROVIDER_CREDENTIAL_REPLACE",
            "SUCCEEDED", admin.actor(), "NOTIFICATION_PROVIDER", NEW_PROVIDER);
        auditChecker.assertNoSecret(event, SECRET_MARKER);
        auditChecker.assertNoSecret(event, NEW_TOKEN);
        assertFalse(response.text().contains(NEW_TOKEN));
        assertFalse(output.getAll().contains(SECRET_MARKER), "密钥不得进入日志");
        assertFalse(output.getAll().contains(NEW_TOKEN), "Token 不得进入日志");
        var item = provider(NEW_PROVIDER);
        assertAll(() -> assertTrue(item.isCredentialConfigured()), () -> assertNotNull(item.getCredentialUpdateTime()),
            () -> assertEquals(List.of(), item.getMissingConfiguration()));
        patch(BASE + "/providers/" + NEW_PROVIDER + "/status", manager, status("ACTIVE", 2)).assertOk();
    }

    /** Vault 拒绝或不可用返回 503 且账号不变；版本过期在调用 Vault 前 409；只有维护权限 403；格式错误 422。 */
    @Test
    void test_replaceCredentialRejected(CapturedOutput output) {
        String path = BASE + "/providers/" + SES_PROVIDER + "/credential";
        vault.credentialProtectFails(403);
        put(path, admin, credential(1)).assertProblem(503, "SERVICE_UNAVAILABLE");
        assertEquals(SES_TOKEN, mapper.providerState(SES_PROVIDER).get("credential_vault_token"));

        put(path, admin, credential(9)).assertProblem(409, "CONCURRENT_REQUEST");
        put(path, manager, credential(1)).assertProblem(403, "OPERATIONS_PERMISSION_DENIED");
        var invalid = credential(1);
        invalid.put("accessKeyId", "akia lower");
        put(path, admin, invalid).assertProblem(422, "VALIDATION_ERROR").assertFieldError("accessKeyId");
        assertTrue(vault.protectedCredentials().isEmpty());
        assertEquals(1, ((Number) mapper.providerState(SES_PROVIDER).get("version")).intValue());
        assertFalse(output.getAll().contains(SECRET_MARKER), "密钥不得进入日志");
    }

    /** 测试发送：按账号配置与 Vault 密钥发送固定内容，不生成发送记录，审计只记结果码与收件邮箱摘要。 */
    @Test
    void test_testSendAccepted(CapturedOutput output) {
        vault.allowCredential(SES_TOKEN, SES_CREDENTIAL);
        ses.accept("ops@example.com", "ses-test-1");

        var result = post(BASE + "/providers/" + SES_PROVIDER + "/test-send", admin, testSend(" Ops@Example.com "))
            .assertOk().as(NotificationProviderTestSendResponseBody.class);

        assertEquals("ACCEPTED", result.getOutcome());
        assertEquals("SES_ACCEPTED", result.getResultCode());
        assertEquals(1, ses.requests().size());
        var request = ses.requests().getFirst();
        assertAll(() -> assertEquals("ops@example.com", request.recipient()),
            () -> assertEquals("noreply@example.com", request.from()),
            () -> assertEquals("PAYTECH 发送账号测试", request.subject()),
            () -> assertTrue(request.text().contains(SES_PROVIDER)),
            () -> assertTrue(request.headers().get("authorization").contains("Credential=" + SES_ACCESS_KEY + "/")));
        assertTrue(mapper.deliveries().isEmpty(), "测试发送不生成发送记录");
        var event = auditChecker.assertEvent(requestId, "OPERATIONS_NOTIFICATION_PROVIDER_TEST_SEND", "SUCCEEDED",
            admin.actor(), "NOTIFICATION_PROVIDER", SES_PROVIDER);
        auditChecker.assertNoSecret(event, "ops@example.com");
        assertFalse(output.getAll().contains("VAULT-SECRET-MARKER"), "密钥不得进入日志");
        assertEquals(1, ((Number) mapper.providerState(SES_PROVIDER).get("version")).intValue(), "测试发送不改账号");
    }

    /** SES 拒绝密钥返回 REJECTED 与结果码；配置不完整 400；不存在 404；只有维护权限 403。 */
    @Test
    void test_testSendRejected() {
        vault.allowCredential(SES_TOKEN, SES_CREDENTIAL);
        ses.fail("ops@example.com", 403, "UnrecognizedClientException", "The security token is invalid");
        var result = post(BASE + "/providers/" + SES_PROVIDER + "/test-send", admin, testSend("ops@example.com"))
            .assertOk().as(NotificationProviderTestSendResponseBody.class);
        assertEquals("REJECTED", result.getOutcome());
        assertEquals("SES_CREDENTIAL_REJECTED", result.getResultCode());

        post(BASE + "/providers/NOTIFICATION_ADMIN_UNBOUND/test-send", admin, testSend("ops@example.com"))
            .assertProblem(400, "BUSINESS_CHECK_FAILED");
        post(BASE + "/providers/NOTIFICATION_ADMIN_NONE/test-send", admin, testSend("ops@example.com"))
            .assertProblem(404, "NOT_FOUND");
        post(BASE + "/providers/" + SES_PROVIDER + "/test-send", manager, testSend("ops@example.com"))
            .assertProblem(403, "OPERATIONS_PERMISSION_DENIED");
        assertEquals(1, ses.requests().size());
    }

    /** 配置不完整的账号不能启用，返回缺失字段。 */
    @Test
    void test_enableRequiresCompleteConfiguration() {
        var problem = patch(BASE + "/providers/NOTIFICATION_ADMIN_UNBOUND/status", manager, status("ACTIVE", 1))
            .assertProblem(400, "BUSINESS_CHECK_FAILED").json();
        assertTrue(problem.path("detail").asString().contains("region"), "提示缺失字段");
        assertEquals(NotificationConfigurationStatus.DISABLED.getCode(),
            mapper.providerState("NOTIFICATION_ADMIN_UNBOUND").get("status"));
    }

    /** 列表中的指定账号。 */
    NotificationProviderResponseBody provider(String code) {
        var page = get(BASE + "/providers?page=1&size=10", reader).assertOk()
            .as(new TypeReference<PagingResponseBody<NotificationProviderResponseBody>>() {});
        return page.items().stream().filter(p -> p.getProviderCode().equals(code)).findFirst().orElseThrow();
    }

    /** 新建请求体；可变以便负例覆盖。 */
    static Map<String, Object> create(String code, String fromAddress, String configurationSet) {
        var body = new HashMap<String, Object>();
        body.put("providerCode", code);
        body.put("providerType", NotificationProviderTypes.AMAZON_SES);
        body.put("region", "eu-west-1");
        body.put("fromAddress", fromAddress);
        body.put("configurationSet", configurationSet);
        body.put("reason", "新增备用区域账号");
        return body;
    }

    /** 编辑请求体。 */
    static Map<String, Object> update(String region, String fromAddress, String configurationSet, int version) {
        var body = new HashMap<String, Object>();
        body.put("region", region);
        body.put("fromAddress", fromAddress);
        body.put("configurationSet", configurationSet);
        body.put("expectedVersion", version);
        body.put("reason", "更换发件地址");
        return body;
    }

    /** 录入密钥请求体；可变以便负例覆盖。 */
    static Map<String, Object> credential(int version) {
        var body = new HashMap<String, Object>();
        body.put("accessKeyId", ACCESS_KEY);
        body.put("secretAccessKey", SECRET_MARKER);
        body.put("expectedVersion", version);
        body.put("reason", "轮换密钥");
        return body;
    }

    /** 测试发送请求体。 */
    static Map<String, Object> testSend(String email) {
        return Map.of("email", email, "reason", "验证密钥");
    }
}
