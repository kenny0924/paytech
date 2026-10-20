package com.paytech.platform.support.auto.ext.http.internalauth;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.platform.support.api.standardapi.IntegrationLookupApi;
import com.paytech.platform.support.api.standardapi.response.IntegrationExistenceResponseBody;
import com.paytech.platform.support.test.base.MockTestBase;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.mocker.local.ExternalApiMocker;
import com.paytech.platform.support.test.util.InternalTokens;

/**
 * internal-auth.mode=REQUIRED 下的接入方存在性查询：与标准 API 执行同样只认内部 Token，不需要运营会话。
 *
 * @see IntegrationLookupApi#integrationExistence(String)
 */
@TestPropertySource(properties = "internal-auth.mode=REQUIRED")
class IntegrationLookupInternalAuthTest extends MockTestBase {

    private static final String SELF = "platform-support-service";
    private static final String CALLER = "integration-lookup-test";
    private static final String PATH = "/standard-apis/v1/integrations/DEMO/existence";

    @Autowired
    InternalTokens tokens;

    @Autowired
    ExternalApiMocker externalApi;

    /** 本类公共：登记接入方 DEMO。 */
    @BeforeEach
    void setUpClassMocks() {
        externalApi.integration("DEMO");
    }

    /** 合法 Token：200，exists=true。 */
    @Test
    void test_normalFlow() {
        var body = lookup(tokens.issue(CALLER, SELF)).assertOk().as(IntegrationExistenceResponseBody.class);

        assertTrue(body.isExists());
    }

    /** 无 Token：401 INTERNAL_TOKEN_INVALID。 */
    @Test
    void test_missingToken() {
        lookup(null).assertProblem(401, CommonErrorCode.INTERNAL_TOKEN_INVALID);
    }

    /** 篡改签名的 Token：401 INTERNAL_TOKEN_INVALID。 */
    @Test
    void test_tamperedToken() {
        String token = tokens.issue(CALLER, SELF);
        String tampered = token.substring(0, token.length() - 4) + (token.endsWith("AAAA") ? "BBBB" : "AAAA");

        lookup(tampered).assertProblem(401, CommonErrorCode.INTERNAL_TOKEN_INVALID);
    }

    /** 以调用方身份 GET；token 为空时不带 Authorization。 */
    private PlatformResponseChecker lookup(String token) {
        String requestId = "plt-lookup-" + UUID.randomUUID();
        HttpHeaders headers = TestRequestHeaders.internal(requestId);
        headers.set("X-Caller-Service", CALLER);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return new PlatformResponseChecker(httpTestInvoker.get(PATH, headers), objectMapper, requestId);
    }
}
