package com.paytech.platform.support.auto.ext.http.notification;

import static com.paytech.platform.support.delivery.integration.provider.EmailProviderResult.Outcome.DELIVERED;
import static com.paytech.platform.support.test.builder.NotificationRequestBuilder.PEER_SOURCE_SYSTEM;
import static com.paytech.platform.support.test.builder.NotificationRequestBuilder.SOURCE_SYSTEM;
import static com.paytech.platform.support.test.builder.NotificationRequestBuilder.TEMPLATE_CODE;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.platform.support.api.notification.NotificationCommandApi;
import com.paytech.platform.support.api.notification.request.SubmitNotificationRequestBody;
import com.paytech.platform.support.api.notification.response.SubmitNotificationResponseBody;
import com.paytech.platform.support.delivery.core.enums.NotificationConfigurationStatus;
import com.paytech.platform.support.delivery.core.enums.NotificationMedium;
import com.paytech.platform.support.test.base.NotificationTestBase;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.vault.client.VaultClientErrorCode;

/**
 * 通知内部 HTTP 提交：经真实 Controller、白名单、delivery Processor、Vault Mock 端点与本地 MySQL。
 * <li>调用方或模板未授权、Body 不合法时不调用 Vault、不落库</li>
 * <li>来源系统只取 X-Caller-Service，错误正文与日志不含邮箱、参数值</li>
 *
 * @see NotificationCommandApi#submitNotification(String, SubmitNotificationRequestBody)
 */
@ExtendWith(OutputCaptureExtension.class)
class NotificationHttpApiTest extends NotificationTestBase {

    private static final String PATH = "/notifications/v1/requests";
    private static final String ALICE = "alice@example.com";
    private static final String SECRET_PARAMETER = "param-value-marker-7781";

    /** 白名单调用方受理后经补偿发送；来源系统为调用方服务名，租户字符串转为数值保存。 */
    @Test
    void test_normalFlow(CapturedOutput output) {
        provider.expectSend(ALICE, DELIVERED);

        var response = submit(SOURCE_SYSTEM, body()).assertOk().as(SubmitNotificationResponseBody.class);

        assertTrue(Long.parseLong(response.getRequestId()) > 0);
        assertEquals(1, response.getAcceptedCount());
        fixture.awaitIdle();
        var source = mapper.requestSources().getFirst();
        assertEquals(SOURCE_SYSTEM, source.get("source_system"));
        assertEquals(10001L, ((Number) source.get("tenant_id")).longValue());
        assertEquals("DELIVERED", mapper.deliveries().getFirst().get("status"));
        assertEquals(1, provider.calls().size());
        assertNoSensitiveLog(output);
    }

    /** 平台通知不带租户：受理后租户为空。 */
    @Test
    void test_platformNotificationWithoutTenant() {
        provider.expectSend(ALICE, DELIVERED);
        var body = body();
        body.remove("tenantId");

        submit(SOURCE_SYSTEM, body).assertOk();

        fixture.awaitIdle();
        assertNull(mapper.requestSources().getFirst().get("tenant_id"));
    }

    /** 缺少调用方 Header：400，不调用 Vault、不落库。 */
    @Test
    void test_missingCaller() {
        submit(null, body()).assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        assertRejectedBeforeVault();
    }

    /** 未登记调用方与越权模板返回同一业务拒绝，不调用 Vault、不落库。 */
    @Test
    void test_callerOrTemplateNotAllowed(CapturedOutput output) {
        submit("payment-orchestration-service", body()).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        submit("", body()).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        var other = body();
        other.put("templateCode", "NOTIFICATION_TEST_OTHER");
        submit(SOURCE_SYSTEM, other).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        assertRejectedBeforeVault();
        assertNoSensitiveLog(output);
    }

    /** Body 带 sourceSystem 时被忽略，落库来源仍为调用方服务名。 */
    @Test
    void test_sourceSystemInBodyIgnored() {
        provider.expectSend(ALICE, DELIVERED);
        var body = body();
        body.put("sourceSystem", PEER_SOURCE_SYSTEM);

        submit(SOURCE_SYSTEM, body).assertOk();

        fixture.awaitIdle();
        assertEquals(List.of(SOURCE_SYSTEM), mapper.requestSources().stream().map(r -> r.get("source_system")).toList());
    }

    /** 同一调用方重复来源键：409 且不产生第二个请求；另一调用方使用同一来源键正常受理。 */
    @Test
    void test_duplicateSourceEventKey() {
        provider.expectSend(ALICE, DELIVERED, DELIVERED);
        submit(SOURCE_SYSTEM, body()).assertOk();
        fixture.awaitIdle();

        submit(SOURCE_SYSTEM, body()).assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST);
        submit(PEER_SOURCE_SYSTEM, body()).assertOk();

        fixture.awaitIdle();
        assertEquals(List.of(SOURCE_SYSTEM, PEER_SOURCE_SYSTEM),
            mapper.requestSources().stream().map(r -> r.get("source_system")).toList());
        assertEquals(2, provider.calls().size());
    }

    /** 字段校验失败（缺字段、空列表、缺收件人、空收件人元素）：422 带字段错误；租户超出 long 范围：400。均不调用 Vault。 */
    @Test
    void test_invalidBody() {
        var missing = body();
        missing.remove("sourceEventKey");
        missing.put("recipients", List.of());
        var checker = submit(SOURCE_SYSTEM, missing).assertProblem(422, CommonErrorCode.VALIDATION_ERROR);
        var fields = new java.util.HashSet<String>();
        checker.json().get("errors").forEach(error -> fields.add(error.get("field").asString()));
        assertEquals(java.util.Set.of("sourceEventKey", "recipients"), fields);

        var absent = body();
        absent.remove("recipients");
        submit(SOURCE_SYSTEM, absent).assertProblem(422, CommonErrorCode.VALIDATION_ERROR);
        var nullRecipient = body();
        nullRecipient.put("recipients", java.util.Arrays.asList((Object) null));
        submit(SOURCE_SYSTEM, nullRecipient).assertProblem(422, CommonErrorCode.VALIDATION_ERROR);

        var tenant = body();
        tenant.put("tenantId", "9999999999999999999");
        submit(SOURCE_SYSTEM, tenant).assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        assertRejectedBeforeVault();
    }

    /** 超过 1 MB 的 Body 返回 413，不调用 Vault。 */
    @Test
    void test_bodyTooLarge() {
        var large = body();
        large.put("defaultParameters", Map.of("notice", "x".repeat(1024 * 1024)));
        submit(SOURCE_SYSTEM, large).assertStatus(413);
        assertRejectedBeforeVault();
    }

    /** 模板停用：400 业务拒绝；Vault 不可达：503 且不落库。 */
    @Test
    void test_templateDisabledOrVaultUnavailable(CapturedOutput output) {
        mapper.changeTemplate(NotificationConfigurationStatus.DISABLED.getCode(), NotificationMedium.EMAIL.getCode());
        submit(SOURCE_SYSTEM, body()).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        assertRejectedBeforeVault();

        mapper.changeTemplate(NotificationConfigurationStatus.ACTIVE.getCode(), NotificationMedium.EMAIL.getCode());
        vault.protectUnavailable();
        submit(SOURCE_SYSTEM, body()).assertProblem(503, VaultClientErrorCode.DEPENDENCY_UNAVAILABLE);
        assertEquals(1, vault.protectCount());
        assertEquals(0, mapper.requests());
        assertNoSensitiveLog(output);
    }

    /** 合法基线 Body；用 Map 构造以便负例增删字段。 */
    private static Map<String, Object> body() {
        var body = new HashMap<String, Object>();
        body.put("tenantId", "10001");
        body.put("sourceEventKey", "maintenance-001");
        body.put("templateCode", TEMPLATE_CODE);
        body.put("defaultLocale", "zh-CN");
        body.put("defaultParameters", Map.of("notice", "Maintenance", "name", SECRET_PARAMETER));
        body.put("recipients", List.of(Map.of("email", ALICE, "parameters", Map.of("name", "Alice"))));
        return body;
    }

    /** 以指定调用方发送一次真实 HTTP 请求；caller 为 null 时不带 Header。 */
    private PlatformResponseChecker submit(String caller, Map<String, Object> body) {
        String requestId = "plt-notify-" + UUID.randomUUID();
        HttpHeaders headers = TestRequestHeaders.internal(requestId);
        if (caller != null) {
            headers.set(NotificationCommandApi.CALLER_SERVICE_HEADER, caller);
        }
        var checker = new PlatformResponseChecker(httpTestInvoker.exchange(HttpMethod.POST, PATH, headers, body),
            objectMapper, requestId);
        assertFalse(checker.text().contains(ALICE), "响应不得回显邮箱");
        assertFalse(checker.text().contains(SECRET_PARAMETER), "响应不得回显参数值");
        return checker;
    }

    /** 前置拒绝：没有 Vault 调用与落库。 */
    private void assertRejectedBeforeVault() {
        vault.assertNoCalls();
        assertEquals(0, mapper.requests());
        assertTrue(mapper.events().isEmpty());
    }

    /** 日志不含邮箱与参数值。 */
    private static void assertNoSensitiveLog(CapturedOutput output) {
        assertFalse(output.getAll().contains(ALICE), "日志不得包含邮箱");
        assertFalse(output.getAll().contains(SECRET_PARAMETER), "日志不得包含参数值");
    }
}
