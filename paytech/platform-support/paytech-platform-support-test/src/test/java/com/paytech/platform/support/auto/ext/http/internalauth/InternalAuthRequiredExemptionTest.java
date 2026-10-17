package com.paytech.platform.support.auto.ext.http.internalauth;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.platform.support.api.internalauth.InternalJwksQueryApi;
import com.paytech.platform.support.api.internalauth.InternalTokenIssueApi;
import com.paytech.platform.support.api.internalauth.request.InternalTokenIssueRequestBody;
import com.paytech.platform.support.test.base.MockTestBase;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.util.InternalTokens;

/**
 * internal-auth.mode=REQUIRED 下签发与 JWKS 仍可在无 Token 时访问（申请方尚无 Token）；其他内部路径仍要求 Token。
 *
 * @see InternalTokenIssueApi#issue(String, InternalTokenIssueRequestBody)
 * @see InternalJwksQueryApi#queryInternalJwks()
 */
@TestPropertySource(properties = "internal-auth.mode=REQUIRED")
class InternalAuthRequiredExemptionTest extends MockTestBase {

    @Autowired
    InternalTokens tokens;

    /** 无 Token 申请与读取公钥：成功。 */
    @Test
    void test_issueAndJwksExempt() {
        tokens.issue("issuer-test", "payment-orchestration-service");
        String requestId = "plt-jwks-" + UUID.randomUUID();
        new PlatformResponseChecker(httpTestInvoker.get(InternalTokens.JWKS_PATH, TestRequestHeaders.internal(requestId)),
            objectMapper, requestId).assertOk();
    }

    /** 豁免只按路径前缀生效：通知受理仍要求 Token。 */
    @Test
    void test_otherInternalPathStillRequiresToken() {
        String requestId = "plt-notify-" + UUID.randomUUID();
        var headers = TestRequestHeaders.internal(requestId);
        headers.set("X-Caller-Service", "notification-test");
        new PlatformResponseChecker(httpTestInvoker.post("/notifications/v1/requests", headers, Map.of()), objectMapper,
            requestId).assertProblem(401, CommonErrorCode.INTERNAL_TOKEN_INVALID);
    }
}
