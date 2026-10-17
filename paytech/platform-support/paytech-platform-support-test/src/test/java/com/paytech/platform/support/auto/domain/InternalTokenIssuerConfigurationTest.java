package com.paytech.platform.support.auto.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.paytech.foundation.internalauth.api.InternalTokenClaims;
import com.paytech.platform.support.core.configuration.InternalTokenIssuerProperties;
import com.paytech.platform.support.core.internalauth.InternalTokenKeyRing;

/**
 * 签发端启动校验：密钥材料必须是含 kid 的 P-256、轮换公钥不含私钥且 kid 不重复；有效期、实例 ID 与白名单格式非法时拒绝启动。
 * 错误信息只给出配置键，不含密钥内容。
 *
 * @see InternalTokenKeyRing#InternalTokenKeyRing(String, List)
 * @see InternalTokenIssuerProperties#validate()
 */
class InternalTokenIssuerConfigurationTest {

    /** 合法材料：签名公钥在前，轮换公钥在后，均按 kid 可查。 */
    @Test
    void test_normalFlow() throws Exception {
        ECKey active = key("kid-active");
        ECKey retiring = key("kid-retiring");

        var ring = new InternalTokenKeyRing(active.toJSONString(), List.of(retiring.toPublicJWK().toJSONString()));

        assertEquals(List.of("kid-active", "kid-retiring"), ring.publishedKeys().stream().map(ECKey::getKeyID).toList());
        assertTrue(ring.publishedKeys().stream().noneMatch(ECKey::isPrivate));
        assertEquals(retiring.toPublicJWK().getX(), ring.findKey("kid-retiring").getX());
        Instant now = Instant.parse("2026-10-17T00:00:00Z");
        String token = ring.sign(new InternalTokenClaims("a-service", "b-service", "main", "GENERAL", "jti-1", now,
            now.plusSeconds(60)));
        assertEquals(3, token.split("\\.").length);
    }

    /** 缺失、只有公钥、非 P-256、缺 kid、轮换公钥含私钥、kid 重复：拒绝启动且不泄露材料。 */
    @Test
    void test_invalidKeyMaterial() throws Exception {
        ECKey active = key("kid-active");
        ECKey p384 = new ECKeyGenerator(Curve.P_384).keyID("kid-384").generate();
        ECKey noKid = new ECKeyGenerator(Curve.P_256).generate();

        assertRejected(null, List.of());
        assertRejected(active.toPublicJWK().toJSONString(), List.of());
        assertRejected(p384.toJSONString(), List.of());
        assertRejected(noKid.toJSONString(), List.of());
        assertRejected("{not-json", List.of());
        assertRejected(active.toJSONString(), List.of(key("kid-other").toJSONString()));
        assertRejected(active.toJSONString(), List.of(key("kid-active").toPublicJWK().toJSONString()));
        var failure = assertThrows(IllegalStateException.class,
            () -> new InternalTokenKeyRing(p384.toJSONString(), List.of()));
        assertFalse(failure.getMessage().contains(p384.getD().toString()), "错误信息不得包含私钥");
    }

    /** 实例 ID、有效期与白名单格式。 */
    @Test
    void test_invalidProperties() {
        assertInvalid(properties -> properties.setInstanceId(null));
        assertInvalid(properties -> properties.setInstanceId("Main"));
        assertInvalid(properties -> properties.setTokenLifetime(Duration.ofSeconds(301)));
        assertInvalid(properties -> properties.setTokenLifetime(Duration.ZERO));
        assertInvalid(properties -> properties.setCallers(Map.of("Bad_Service", caller(Set.of("b-service"), Set.of("GENERAL")))));
        assertInvalid(properties -> properties.setCallers(Map.of("a-service", caller(Set.of(), Set.of("GENERAL")))));
        assertInvalid(properties -> properties.setCallers(Map.of("a-service", caller(Set.of("B"), Set.of("GENERAL")))));
        assertInvalid(properties -> properties.setCallers(Map.of("a-service", caller(Set.of("b-service"), Set.of("general")))));
        valid().validate();
    }

    /** 白名单授权：目标服务与 Purpose 都必须登记。 */
    @Test
    void test_permits() {
        var properties = valid();
        assertTrue(properties.permits("a-service", "b-service", "GENERAL"));
        assertFalse(properties.permits("a-service", "c-service", "GENERAL"));
        assertFalse(properties.permits("a-service", "b-service", "CARD_READ"));
        assertFalse(properties.permits("x-service", "b-service", "GENERAL"));
        assertFalse(properties.permits(null, "b-service", "GENERAL"));
    }

    /** 生成带 kid 的 P-256 私钥。 */
    private static ECKey key(String kid) throws Exception {
        return new ECKeyGenerator(Curve.P_256).keyID(kid).generate();
    }

    /** 期望密钥材料被拒绝。 */
    private static void assertRejected(String privateJwk, List<String> retiring) {
        assertThrows(IllegalStateException.class, () -> new InternalTokenKeyRing(privateJwk, retiring));
    }

    /** 合法基线配置。 */
    private static InternalTokenIssuerProperties valid() {
        var properties = new InternalTokenIssuerProperties();
        properties.setInstanceId("main");
        properties.setCallers(Map.of("a-service", caller(Set.of("b-service"), Set.of("GENERAL"))));
        return properties;
    }

    /** 在合法基线上改动一项后期望启动失败。 */
    private static void assertInvalid(java.util.function.Consumer<InternalTokenIssuerProperties> change) {
        var properties = valid();
        change.accept(properties);
        assertThrows(IllegalStateException.class, properties::validate);
    }

    /** 一个调用方授权。 */
    private static InternalTokenIssuerProperties.Caller caller(Set<String> targets, Set<String> purposes) {
        var caller = new InternalTokenIssuerProperties.Caller();
        caller.setTargets(targets);
        caller.setPurposes(purposes);
        return caller;
    }
}
