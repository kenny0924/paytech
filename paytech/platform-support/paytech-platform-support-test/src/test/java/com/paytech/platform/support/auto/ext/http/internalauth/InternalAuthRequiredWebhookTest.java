package com.paytech.platform.support.auto.ext.http.internalauth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.TestPropertySource;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.platform.support.api.webhook.WebhookEventApi;
import com.paytech.platform.support.api.webhook.request.SubmitWebhookEventRequestBody;
import com.paytech.platform.support.test.base.WebhookTestBase;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.util.InternalTokens;
import com.paytech.platform.support.test.webhook.WebhookEventBuilder;
import com.paytech.platform.support.test.webhook.WebhookFixture;

/**
 * internal-auth.mode=REQUIRED 下的 Webhook 事件受理：必须携带访问 Platform 的合法 Token，否则 401 且不写库。
 *
 * @see WebhookEventApi#submitEvent(String, SubmitWebhookEventRequestBody)
 */
@TestPropertySource(properties = "internal-auth.mode=REQUIRED")
class InternalAuthRequiredWebhookTest extends WebhookTestBase {

    private static final String SELF = "platform-support-service";

    @Autowired
    InternalTokens tokens;

    /** 合法 Token：受理，来源系统为 Token 调用方。 */
    @Test
    void test_normalFlow() {
        String token = tokens.issue(WebhookFixture.CALLER, SELF);

        submit(token, "evt_required_token").assertNoContent(204);

        assertEquals(WebhookFixture.CALLER, webhookData.events().getFirst().get("source_system"));
    }

    /** 只有 X-Caller-Service：401，不回退明文头。 */
    @Test
    void test_missingToken() {
        submit(null, "evt_required_missing").assertProblem(401, CommonErrorCode.INTERNAL_TOKEN_INVALID);

        assertEquals(0, webhookData.events().size());
    }

    /** 发给其他服务的 Token（aud 不是 Platform）：401。 */
    @Test
    void test_tokenForOtherAudience() {
        String token = tokens.issue("issuer-test", "payment-service");

        submit(token, "evt_required_audience").assertProblem(401, CommonErrorCode.INTERNAL_TOKEN_INVALID);

        assertEquals(0, webhookData.events().size());
    }

    /** 以 Webhook 测试调用方提交一个无订阅事件；token 为空时不带 Authorization。 */
    PlatformResponseChecker submit(String token, String eventId) {
        HttpHeaders headers = TestRequestHeaders.internal("plt-whk-" + eventId);
        headers.set(WebhookEventApi.CALLER_SERVICE_HEADER, WebhookFixture.CALLER);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return call(HttpMethod.POST, EVENTS, headers, WebhookEventBuilder.orderPaid(eventId).build());
    }
}
