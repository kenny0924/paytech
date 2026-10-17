package com.paytech.gateway.auto.security;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.SignedJWT;
import com.paytech.foundation.internalauth.api.CallerIdentity;
import com.paytech.foundation.internalauth.api.InternalAuthMode;
import com.paytech.foundation.internalauth.api.InternalTokenClaims;
import com.paytech.foundation.internalauth.core.supplier.StaticKeyInternalTokenSupport;
import com.paytech.foundation.internalauth.core.token.InternalTokenVerifierImpl;
import com.paytech.gateway.routing.snapshot.GatewaySnapshotRefresher;
import com.paytech.gateway.security.ReactiveJwksProvider;
import com.paytech.gateway.test.GatewayAutoTestBase;
import com.paytech.gateway.test.GatewayAutoTestRuntime;
import com.paytech.gateway.test.GatewayPublicTestClient;
import com.paytech.gateway.test.OperationsTestPaths;
import com.paytech.gateway.test.builder.GatewaySnapshotFixture;
import com.paytech.gateway.test.builder.JwtFixtureBuilder;
import com.paytech.gateway.test.simulator.bff.BffRequestJournal;
import com.paytech.gateway.test.simulator.bff.BffScenarioStore;
import com.paytech.gateway.test.simulator.clientapi.ClientApiOwnerScenarioStore.Outcome;
import com.paytech.gateway.test.simulator.platform.PlatformCredentialValidationSimulator;
import com.paytech.gateway.web.GatewayProtocol;

/**
 * {@code internal-auth.mode=REQUIRED}（本地静态密钥对）时，Gateway 调 Platform 的三个 WebClient 与转发到内部服务的请求都附加
 * 内部 Token：{@code aud} 为目标服务、调用方为 {@code api-gateway}、Purpose 为 {@code GENERAL}。
 * <li>Token 由测试侧以同一公钥经 Foundation 校验器验证，断言基于 Simulator 实际收到的 Header。</li>
 * <li>Gateway 测试只允许唯一 {@code auto} Profile（PLT-I03 直连地址门禁），因此以动态属性开启而不是另建 Profile。</li>
 *
 * @see GatewaySnapshotRefresher#refresh()
 * @see ReactiveJwksProvider#select(SignedJWT)
 * @see com.paytech.gateway.web.InternalTokenUpstreamGlobalFilter#filter(org.springframework.web.server.ServerWebExchange,
 *      org.springframework.cloud.gateway.filter.GatewayFilterChain)
 */
class InternalTokenOutboundAutoTest extends GatewayAutoTestBase {

    private static final String CALLER = "api-gateway";

    private static final String PLATFORM = "platform-support-service";

    private static final String INSTANCE = "main";

    private static final String JWKS_KID = "internal-token-outbound-kid";

    private static final ECKey KEY = generateKey();

    @LocalServerPort
    private int port;

    @Autowired
    private ReactiveJwksProvider reactiveJwksProvider;

    @DynamicPropertySource
    static void registerInternalAuth(DynamicPropertyRegistry registry) {
        registry.add("internal-auth.mode", InternalAuthMode.REQUIRED::name);
        registry.add("paytech.instance-id", () -> INSTANCE);
        registry.add("internal-auth.static-key.private-jwk", KEY::toJSONString);
        registry.add("internal-auth.static-key.public-jwk", () -> KEY.toPublicJWK().toJSONString());
    }

    @Test
    void test_normalFlow_snapshotRequestCarriesPlatformToken() {
        // 全局 BeforeEach 已经过真实 Refresher 拉取一次 Snapshot。
        String authorization = GatewayAutoTestRuntime.platformSnapshotSimulator().lastAuthorization()
            .orElseThrow(() -> new AssertionError("Snapshot 请求未携带 Authorization"));

        assertInternalToken(authorization, PLATFORM);
    }

    @Test
    void test_normalFlow_jwksRequestCarriesPlatformToken() throws Exception {
        GatewayAutoTestRuntime.jwksSimulator().scenarios().respondWithJwks(JwtFixtureBuilder.jwksJson(JWKS_KID));

        reactiveJwksProvider.select(SignedJWT.parse(JwtFixtureBuilder.template().keyId(JWKS_KID).build())).single()
            .block(Duration.ofSeconds(2));

        GatewayAutoTestRuntime.jwksSimulator().journal().verifySingleGet();
        assertInternalToken(GatewayAutoTestRuntime.jwksSimulator().lastAuthorization()
            .orElseThrow(() -> new AssertionError("JWKS 请求未携带 Authorization")), PLATFORM);
    }

    @Test
    void test_normalFlow_credentialValidationCarriesPlatformTokenNotClientCredential() {
        GatewayAutoTestRuntime.platformSnapshotSimulator().publish(GatewaySnapshotFixture.clientApiSnapshot());
        Assertions.assertTrue(refreshSnapshot());
        GatewayAutoTestRuntime.credentialSimulator().expectValid("orders.write");
        GatewayAutoTestRuntime.clientApiOwnerSimulator().scenarios().expect(HttpMethod.POST,
            GatewaySnapshotFixture.CLIENT_API_DOWNSTREAM_PATH, Outcome.ACCEPTED);

        GatewayPublicTestClient.clientApi(port).defaultHeader(GatewayProtocol.CLIENT_IP, "203.0.113.17").build()
            .post().uri(GatewaySnapshotFixture.CLIENT_API_PATH)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + PlatformCredentialValidationSimulator.CREDENTIAL)
            .bodyValue("{}").exchange().expectStatus().isOk();

        assertInternalToken(GatewayAutoTestRuntime.credentialSimulator().lastAuthorization()
            .orElseThrow(() -> new AssertionError("PLT-I03 请求未携带 Authorization")), PLATFORM);
        Assertions.assertTrue(GatewayAutoTestRuntime.clientApiOwnerSimulator().journal().entries().getFirst()
            .headerNames().contains("authorization"), "Client API Owner 未收到内部 Token");
    }

    @Test
    void test_normalFlow_bffForwardCarriesTokenForRouteTarget() {
        GatewayAutoTestRuntime.operationsBffSimulator().scenarios().expect(HttpMethod.POST, "/auth/v1/login",
            BffScenarioStore.Response.json(201, "{\"accepted\":true}"));

        GatewayPublicTestClient.operations(port).defaultHeader(GatewayProtocol.CLIENT_IP, "203.0.113.17").build()
            .post().uri(OperationsTestPaths.LOGIN).header(HttpHeaders.ORIGIN, "https://operations-console.test")
            .bodyValue("{\"request\":\"value\"}").exchange().expectStatus().isCreated();

        List<BffRequestJournal.Entry> entries = GatewayAutoTestRuntime.operationsBffSimulator().journal().entries();
        Assertions.assertEquals(1, entries.size());
        List<String> authorization = headerValues(entries.getFirst().headers(), HttpHeaders.AUTHORIZATION);
        Assertions.assertEquals(1, authorization.size(), "BFF 应只收到一个 Authorization: " + authorization.size());
        assertInternalToken(authorization.getFirst(), GatewaySnapshotFixture.OPERATIONS_TARGET);
    }

    /**
     * 以测试公钥校验 Bearer Token：签名、签发方、目标服务与实例有效，调用方为 Gateway，Purpose 为 GENERAL。
     *
     * @param authorization Authorization 原值
     * @param audience 期望目标服务
     */
    private static void assertInternalToken(String authorization, String audience) {
        Assertions.assertTrue(authorization.startsWith("Bearer "), "Authorization 不是 Bearer");
        StaticKeyInternalTokenSupport keySource = new StaticKeyInternalTokenSupport(KEY, KEY.toPublicJWK(), CALLER,
            INSTANCE, Clock.systemUTC());
        CallerIdentity caller = new InternalTokenVerifierImpl(keySource, audience, INSTANCE, Clock.systemUTC())
            .verify(authorization.substring("Bearer ".length()));
        Assertions.assertAll(() -> Assertions.assertTrue(caller.verified()),
            () -> Assertions.assertEquals(CALLER, caller.caller()),
            () -> Assertions.assertEquals(InternalTokenClaims.GENERAL_PURPOSE, caller.purpose()));
    }

    private static List<String> headerValues(Map<String, List<String>> headers, String name) {
        return headers.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(name))
            .flatMap(entry -> entry.getValue().stream()).toList();
    }

    private static ECKey generateKey() {
        try {
            return new ECKeyGenerator(Curve.P_256).keyID("gateway-internal-test").keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.ES256).generate();
        } catch (JOSEException exception) {
            throw new IllegalStateException("无法生成测试 ES256 密钥", exception);
        }
    }
}
