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
 * internal-auth.mode=DUAL 下的 Webhook 事件受理：合法 Token 以 sub 为来源系统；无 Token 或 Token 无效时回退 X-Caller-Service。
 * <li>Token 调用方与 X-Caller-Service 不一致时拒绝，不写库。</li>
 *
 * @see WebhookEventApi#submitEvent(String, SubmitWebhookEventRequestBody)
 */
@TestPropertySource(properties = "internal-auth.mode=DUAL")
class InternalAuthDualWebhookTest extends WebhookTestBase {

    private static final String SELF = "platform-support-service";

    @Autowired
    InternalTokens tokens;

    /** 合法 Token：受理，来源系统为 Token 调用方。 */
    @Test
    void test_normalFlow() {
        String token = tokens.issue(WebhookFixture.CALLER, SELF);

        submit(WebhookFixture.CALLER, token, "evt_dual_token").assertNoContent(204);

        assertEquals(WebhookFixture.CALLER, webhookData.events().getFirst().get("source_system"));
    }

    /** 无 Token：迁移期回退 X-Caller-Service 白名单。 */
    @Test
    void test_legacyHeaderFallback() {
        submit(WebhookFixture.CALLER, null, "evt_dual_legacy").assertNoContent(204);

        assertEquals(WebhookFixture.CALLER, webhookData.events().getFirst().get("source_system"));
    }

    /** Token 无效：DUAL 不拒绝，按 X-Caller-Service 回退。 */
    @Test
    void test_invalidTokenFallsBack() {
        submit(WebhookFixture.CALLER, "not-a-jwt", "evt_dual_invalid").assertNoContent(204);

        assertEquals(1, webhookData.events().size());
    }

    /** 借他人 Token 冒充：Header 与 Token sub 不一致时 400，不写库。 */
    @Test
    void test_callerMismatch() {
        String token = tokens.issue(WebhookFixture.PEER_CALLER, SELF);

        submit(WebhookFixture.CALLER, token, "evt_dual_mismatch").assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);

        assertEquals(0, webhookData.events().size());
    }

    /** 提交一个无订阅的订单支付事件；token 为空时不带 Authorization。 */
    PlatformResponseChecker submit(String caller, String token, String eventId) {
        HttpHeaders headers = TestRequestHeaders.internal("plt-whk-" + eventId);
        headers.set(WebhookEventApi.CALLER_SERVICE_HEADER, caller);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return call(HttpMethod.POST, EVENTS, headers, WebhookEventBuilder.orderPaid(eventId).build());
    }
}
