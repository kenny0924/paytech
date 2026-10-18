package com.paytech.platform.support.auto.ext.http.internalauth;

import static com.paytech.platform.support.delivery.integration.provider.EmailProviderResult.Outcome.DELIVERED;
import static com.paytech.platform.support.test.builder.NotificationRequestBuilder.PEER_SOURCE_SYSTEM;
import static com.paytech.platform.support.test.builder.NotificationRequestBuilder.SOURCE_SYSTEM;
import static com.paytech.platform.support.test.builder.NotificationRequestBuilder.TEMPLATE_CODE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.TestPropertySource;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.platform.support.api.notification.NotificationCommandApi;
import com.paytech.platform.support.api.notification.request.SubmitNotificationRequestBody;
import com.paytech.platform.support.test.base.NotificationTestBase;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.util.InternalTokens;

/**
 * internal-auth.mode=DUAL 下的普通邮件受理：合法 Token 以 sub 为来源系统；无 Token 时回退 X-Caller-Service 白名单。
 * <li>Token 调用方与 X-Caller-Service 不一致时拒绝，不调用 Vault、不落库。</li>
 *
 * @see NotificationCommandApi#submitNotification(String, SubmitNotificationRequestBody)
 */
@TestPropertySource(properties = "internal-auth.mode=DUAL")
class InternalAuthDualNotificationTest extends NotificationTestBase {

    private static final String SELF = "platform-support-service";
    private static final String PATH = "/notifications/v1/requests";
    private static final String ALICE = "alice@example.com";

    @Autowired
    InternalTokens tokens;

    /** 合法 Token：受理并发送，来源系统为 Token 调用方。 */
    @Test
    void test_normalFlow() {
        provider.expectSend(ALICE, DELIVERED);
        String token = tokens.issue(SOURCE_SYSTEM, SELF);

        submit(SOURCE_SYSTEM, token).assertOk();

        fixture.awaitIdle();
        assertEquals(List.of(SOURCE_SYSTEM), mapper.requestSources().stream().map(row -> row.get("source_system")).toList());
        assertEquals(1, provider.calls().size());
    }

    /** 借他人 Token 冒充：Header 与 Token sub 不一致时 400，不调用 Vault、不落库。 */
    @Test
    void test_callerMismatch() {
        String token = tokens.issue(SOURCE_SYSTEM, SELF);

        submit(PEER_SOURCE_SYSTEM, token).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);

        assertRejectedBeforeVault();
    }

    /** 无 Token：迁移期回退 X-Caller-Service 白名单受理。 */
    @Test
    void test_legacyHeaderFallback() {
        provider.expectSend(ALICE, DELIVERED);

        submit(SOURCE_SYSTEM, null).assertOk();

        fixture.awaitIdle();
        assertEquals(List.of(SOURCE_SYSTEM), mapper.requestSources().stream().map(row -> row.get("source_system")).toList());
    }


    /** 以白名单模板提交普通邮件；token 为空时不带 Authorization。 */
    PlatformResponseChecker submit(String caller, String token) {
        String requestId = "plt-notify-" + UUID.randomUUID();
        HttpHeaders headers = TestRequestHeaders.internal(requestId);
        headers.set(NotificationCommandApi.CALLER_SERVICE_HEADER, caller);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return new PlatformResponseChecker(httpTestInvoker.exchange(HttpMethod.POST, PATH, headers, body()),
            objectMapper, requestId);
    }

    /** 最小合法请求体：一位收件人。 */
    private static Map<String, Object> body() {
        var body = new HashMap<String, Object>();
        body.put("tenantId", "10001");
        body.put("sourceEventKey", "internal-auth-" + UUID.randomUUID());
        body.put("templateCode", TEMPLATE_CODE);
        body.put("defaultLocale", "zh-CN");
        body.put("defaultParameters", Map.of("notice", "Maintenance", "name", "Alice"));
        body.put("recipients", List.of(Map.of("email", ALICE, "parameters", Map.of("name", "Alice"))));
        return body;
    }

    /** 前置拒绝：没有 Vault 调用与落库。 */
    private void assertRejectedBeforeVault() {
        vault.assertNoCalls();
        assertEquals(0, mapper.requests());
        assertTrue(mapper.deliveries().isEmpty());
    }
}
