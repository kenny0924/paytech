package com.paytech.gateway.auto.security;

import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.web.reactive.function.client.WebClient;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.paytech.gateway.observability.GatewayMetrics;
import com.paytech.gateway.security.JwksUnavailableException;
import com.paytech.gateway.security.ReactiveJwksProvider;
import com.paytech.gateway.test.builder.JwtFixtureBuilder;
import com.paytech.gateway.test.simulator.jwks.JwksSimulator;
import com.paytech.gateway.test.time.MutableTestClock;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import reactor.core.Exceptions;
import reactor.test.scheduler.VirtualTimeScheduler;

/** 不依赖 Redis 的 Platform Support JWKS HTTP 契约测试。 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReactiveJwksProviderContractAutoTest {
    private final JwksSimulator simulator = new JwksSimulator();

    @BeforeAll
    void startSimulator() {
        simulator.start();
    }

    @AfterEach
    void verifyScenario() {
        simulator.verifyComplete();
        simulator.reset();
    }

    @AfterAll
    void stopSimulator() {
        simulator.stop();
    }

    @Test
    void test_exactPlatformContractIsFetchedFromFixedPrivatePath() {
        simulator.scenarios().expectJwks(JwtFixtureBuilder.jwksJson("contract-key"));

        List<JWK> selected = provider().select(unsignedJwt("contract-key")).collectList().block(Duration.ofSeconds(1));

        Assertions.assertNotNull(selected);
        Assertions.assertEquals(List.of("contract-key"), selected.stream().map(JWK::getKeyID).toList());
        simulator.journal().verifySingleGet();
    }

    @Test
    void test_unknownPublicKeyFieldFailsClosed() {
        String valid = JwtFixtureBuilder.jwksJson("contract-key");
        String payload = valid.replace("\"kid\":\"contract-key\"", "\"kid\":\"contract-key\",\"future\":true");
        simulator.scenarios().expectJwks(payload);

        RuntimeException failure = Assertions.assertThrows(RuntimeException.class,
            () -> provider().select(unsignedJwt("contract-key")).collectList().block(Duration.ofSeconds(1)));

        Assertions.assertInstanceOf(JwksUnavailableException.class, Exceptions.unwrap(failure));
        simulator.journal().verifySingleGet();
    }

    /** 使用真实私有 HTTP 输入验证顶层形状和必填字段，不替换解析器。 */
    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "{\"other\":[]}", "{\"keys\":{}}"})
    void test_malformedJwksEnvelopeFailsClosed(String payload) {
        assertInvalidPayload(payload);
    }

    /** 经真实 JWKS HTTP 输入拒绝错误类型、签名用途和非规范 RSA 整数。 */
    @ParameterizedTest
    @CsvSource({"key_ops,STRING", "key_ops,NUMBER_LIST", "kty,NUMBER", "kty,EC", "use,enc",
        "n,LEADING_ZERO", "e,NONCANONICAL"})
    void test_jwkTypesUsageAndRsaIntegerEncodingAreStrict(String field, String defect) {
        var json = JsonMapper.builder().build();
        var tree = json.readTree(JwtFixtureBuilder.jwksJson("contract-key"));
        var key = (tools.jackson.databind.node.ObjectNode) tree.get("keys").get(0);
        switch (defect) {
            case "STRING" -> key.put(field, "verify");
            case "NUMBER_LIST" -> key.putArray(field).add(1);
            case "NUMBER" -> key.put(field, 1);
            case "LEADING_ZERO" -> key.put(field, "AAE");
            case "NONCANONICAL" -> key.put(field, "AR");
            default -> key.put(field, defect);
        }
        assertInvalidPayload(json.writeValueAsString(tree));
    }

    /** 未知字段不能凑足数量来代替必填 JWK 成员。 */
    @Test
    void test_missingRequiredMemberCannotBeReplacedByUnknownMember() {
        var json = JsonMapper.builder().build();
        var tree = json.readTree(JwtFixtureBuilder.jwksJson("contract-key"));
        var key = (tools.jackson.databind.node.ObjectNode) tree.get("keys").get(0);
        key.remove("kid");
        key.put("unrecognized", "contract-key");
        assertInvalidPayload(json.writeValueAsString(tree));
    }

    /** 断言一次真实 JWKS 查询整体失败，且不会选择不可信公钥。 */
    private void assertInvalidPayload(String payload) {
        simulator.scenarios().expectJwks(payload);
        RuntimeException failure = Assertions.assertThrows(RuntimeException.class,
            () -> provider().select(unsignedJwt("contract-key")).collectList().block(Duration.ofSeconds(1)));
        Assertions.assertInstanceOf(JwksUnavailableException.class, Exceptions.unwrap(failure));
        simulator.journal().verifySingleGet();
    }

    private ReactiveJwksProvider provider() {
        WebClient webClient = WebClient.builder().baseUrl(simulator.uri().toString())
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(64 * 1024)).build();
        MutableTestClock clock = new MutableTestClock(JwtFixtureBuilder.NOW, ZoneOffset.UTC);
        return new ReactiveJwksProvider(webClient, clock, VirtualTimeScheduler.create(),
            new GatewayMetrics(new SimpleMeterRegistry()));
    }

    private SignedJWT unsignedJwt(String kid) {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
        return new SignedJWT(header, new JWTClaimsSet.Builder().subject("fixture").build());
    }
}
