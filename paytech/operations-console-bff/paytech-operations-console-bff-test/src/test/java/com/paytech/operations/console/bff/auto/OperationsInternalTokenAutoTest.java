package com.paytech.operations.console.bff.auto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.internalauth.api.CallerIdentity;
import com.paytech.foundation.internalauth.api.InternalAuthMode;
import com.paytech.foundation.internalauth.api.InternalTokenClaims;
import com.paytech.foundation.internalauth.core.supplier.StaticKeyInternalTokenSupport;
import com.paytech.foundation.internalauth.core.token.InternalTokenVerifierImpl;
import com.paytech.foundation.testsupport.api.MockHttpServer;
import com.paytech.operations.console.bff.OperationsConsoleBffApplication;
import com.paytech.platform.support.api.authentication.response.AuthenticationTokensResponseBody;

import tools.jackson.databind.json.JsonMapper;

/**
 * 内部调用 Token（D26）在 {@code internal-auth.mode=REQUIRED} 下的真实 HTTP 链：入站由 MVC Starter 的
 * {@code InternalAuthFilter} 校验，出站 Platform 客户端按目标服务名附加 Token，由 MockHttpServer 记录并校验。
 * 本地静态密钥对同时充当 Gateway 签发方与 BFF 自身签发方，替代 Platform 签发与 JWKS。
 *
 * @author Claude
 */
@ActiveProfiles("auto")
@SpringBootTest(classes = OperationsConsoleBffApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OperationsInternalTokenAutoTest {

    private static final String SELF = "operations-console-bff";
    private static final String PLATFORM_SERVICE = "platform-support-service";
    private static final String GATEWAY = "api-gateway";
    private static final String INSTANCE = "main";
    private static final String ORIGIN = "https://operations-console.local.test";
    private static final String PLATFORM_LOGIN_PATH = "/authentication/v1/operations/login";
    private static final String BFF_LOGIN_PATH = "/auth/v1/login";
    /** Platform Refresh Token 形状：32 位十六进制查找键 + "." + 32 字节 Base64URL 密文。 */
    private static final String REFRESH_TOKEN = "0123456789abcdef0123456789abcdef."
        + Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);

    private static final ECKey KEY = generate();
    private static final PlatformLoginController PLATFORM_LOGIN = new PlatformLoginController();
    private static final MockHttpServer PLATFORM = new MockHttpServer(PLATFORM_LOGIN);
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void internalAuth(DynamicPropertyRegistry registry) {
        registry.add("internal-auth.mode", InternalAuthMode.REQUIRED::name);
        registry.add("internal-auth.static-key.private-jwk", KEY::toJSONString);
        registry.add("internal-auth.static-key.public-jwk", () -> KEY.toPublicJWK().toJSONString());
        registry.add("paytech.instance-id", () -> INSTANCE);
        registry.add("operations.platform-test-base-url", PLATFORM::baseUrl);
        registry.add("operations.risk-test-base-url", PLATFORM::baseUrl);
        registry.add("operations.client-review-risk-test-base-url", PLATFORM::baseUrl);
        registry.add("operations.cct-test-base-url", PLATFORM::baseUrl);
        registry.add("operations.console-origin", () -> ORIGIN);
    }

    @BeforeEach
    void resetPlatform() {
        PLATFORM_LOGIN.authorizations.clear();
    }

    @AfterEach
    void verifyPlatform() {
        PLATFORM.awaitIdle();
        PLATFORM.assertNoUnexpectedRequests();
        PLATFORM.clear();
    }

    @Test
    void test_normalFlow_requiredModeAttachesTokenForPlatform() throws Exception {
        String inbound = issuer(GATEWAY).token(SELF, InternalTokenClaims.GENERAL_PURPOSE);

        HttpResponse<String> response = login("Bearer " + inbound);

        assertEquals(200, response.statusCode(), response.body());
        assertEquals(1, PLATFORM_LOGIN.authorizations.size());
        String outbound = PLATFORM_LOGIN.authorizations.getFirst();
        assertTrue(outbound.startsWith("Bearer "), "出站必须携带 Bearer Token");
        assertNotEquals("Bearer " + inbound, outbound, "出站 Token 由本 BFF 为目标服务签发，不转发入站 Token");
        CallerIdentity caller = new InternalTokenVerifierImpl(issuer(SELF), PLATFORM_SERVICE, INSTANCE,
            Clock.systemUTC()).verify(outbound.substring("Bearer ".length()));
        assertEquals(SELF, caller.caller());
        assertEquals(InternalTokenClaims.GENERAL_PURPOSE, caller.purpose());
    }

    @Test
    void test_invalidParameter_requiredModeRejectsInboundWithoutToken() throws Exception {
        HttpResponse<String> response = login(null);

        assertEquals(401, response.statusCode(), response.body());
        assertEquals(CommonErrorCode.INTERNAL_TOKEN_INVALID.getCode(), JSON.readTree(response.body()).path("code").asString());
        assertTrue(PLATFORM_LOGIN.authorizations.isEmpty(), "入站被拒绝时不得调用 Platform");
    }

    @Test
    void test_businessRuleViolation_requiredModeRejectsTokenForAnotherService() throws Exception {
        String inbound = issuer(GATEWAY).token(PLATFORM_SERVICE, InternalTokenClaims.GENERAL_PURPOSE);

        HttpResponse<String> response = login("Bearer " + inbound);

        assertEquals(401, response.statusCode(), response.body());
        assertTrue(PLATFORM_LOGIN.authorizations.isEmpty(), "aud 不是本 BFF 的 Token 不得放行");
    }

    private HttpResponse<String> login(String authorization) throws Exception {
        String body = JSON.writeValueAsString(Map.of("loginIdentifier", "operator@example.test", "password", "p"));
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + BFF_LOGIN_PATH))
            .timeout(Duration.ofSeconds(8)).header(HttpHeaders.ORIGIN, ORIGIN)
            .header(HttpHeaders.CONTENT_TYPE, "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (authorization != null) {
            request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static StaticKeyInternalTokenSupport issuer(String callerService) {
        return new StaticKeyInternalTokenSupport(KEY, KEY.toPublicJWK(), callerService, INSTANCE, Clock.systemUTC());
    }

    private static ECKey generate() {
        try {
            return new ECKeyGenerator(Curve.P_256).keyID("operations-bff-test").generate();
        } catch (JOSEException exception) {
            throw new IllegalStateException("无法生成测试 ES256 密钥对", exception);
        }
    }

    /** Platform 登录合同的最小 DTO 响应，只记录收到的 Authorization 头。 */
    @RestController
    static final class PlatformLoginController {

        private final List<String> authorizations = new CopyOnWriteArrayList<>();

        @PostMapping(PLATFORM_LOGIN_PATH)
        AuthenticationTokensResponseBody login(@RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false)
            String authorization, @RequestBody Map<String, Object> request) {
            authorizations.add(String.valueOf(authorization));
            return new AuthenticationTokensResponseBody("access-token", "Bearer", 900, REFRESH_TOKEN, 28800, false,
                false);
        }
    }
}
