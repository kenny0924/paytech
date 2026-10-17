package com.paytech.foundation.internalauth.auto.key;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.paytech.foundation.internalauth.api.InternalTokenUnavailableException;
import com.paytech.foundation.internalauth.core.key.JwksInternalTokenKeySource;
import com.paytech.foundation.internalauth.core.token.InternalTokenVerifierImpl;
import com.paytech.foundation.internalauth.test.MutableClock;
import com.paytech.foundation.internalauth.test.TestKeys;
import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.foundation.testsupport.api.MockHttpServer;

/**
 * JWKS 公钥来源：缓存、kid 轮换强制刷新、刷新间隔与不可用时失败关闭；JWKS 由测试内嵌 HTTP 端点提供。
 *
 * @see JwksInternalTokenKeySource#findKey(String)
 */
class JwksInternalTokenKeySourceAutoTest {

    private static final Instant NOW = Instant.parse("2026-10-17T10:00:00Z");

    private final ECKey first = TestKeys.generate("kid-1");
    private final ECKey second = TestKeys.generate("kid-2");
    private final JwksController controller = new JwksController();
    private final MockHttpServer server = new MockHttpServer(controller);
    private final MutableClock clock = new MutableClock(NOW);
    private final JwksInternalTokenKeySource keySource = new JwksInternalTokenKeySource(
        URI.create(server.baseUrl() + "/jwks"), Duration.ofMinutes(5), Duration.ofSeconds(2), Duration.ofSeconds(10),
        clock);

    @AfterEach
    void closeServer() {
        server.assertNoUnexpectedRequests();
        server.close();
    }

    @Test
    void test_normalFlow_cachedWithinTtl() {
        controller.publish(first);

        assertEquals(first.toPublicJWK(), keySource.findKey("kid-1"));
        assertEquals(first.toPublicJWK(), keySource.findKey("kid-1"));

        assertEquals(1, controller.calls.get());
    }

    @Test
    void test_normalFlow_kidRotationForcesOneRefresh() {
        controller.publish(first);
        keySource.findKey("kid-1");
        controller.publish(first, second);

        assertEquals(second.toPublicJWK(), keySource.findKey("kid-2"));

        assertEquals(2, controller.calls.get());
        InternalTokenVerifierImpl verifier = new InternalTokenVerifierImpl(keySource, TestKeys.TARGET,
            TestKeys.INSTANCE, clock);
        assertEquals(TestKeys.CALLER,
            verifier.verify(TestKeys.token(second, TestKeys.TARGET, TestKeys.INSTANCE, "GENERAL", NOW)).caller());
    }

    @Test
    void test_businessRuleViolation_unknownKidRefreshIsRateLimited() {
        controller.publish(first);

        assertNull(keySource.findKey("kid-9"));
        assertNull(keySource.findKey("kid-8"));
        assertEquals(2, controller.calls.get());

        clock.advance(Duration.ofSeconds(10));
        assertNull(keySource.findKey("kid-7"));
        assertEquals(3, controller.calls.get());
    }

    @Test
    void test_edgeCase_refreshAfterTtl() {
        controller.publish(first);
        keySource.findKey("kid-1");
        controller.publish(second);
        clock.advance(Duration.ofMinutes(5));

        // 过期刷新一次；kid-1 已轮换下线，再强制刷新一次确认不存在。
        assertNull(keySource.findKey("kid-1"));
        assertNotNull(keySource.findKey("kid-2"));
        assertEquals(3, controller.calls.get());
    }

    @Test
    void test_businessRuleViolation_unavailableFailsClosed() {
        controller.fail();

        assertThrows(InternalTokenUnavailableException.class, () -> keySource.findKey("kid-1"));
        InternalTokenVerifierImpl verifier = new InternalTokenVerifierImpl(keySource, TestKeys.TARGET,
            TestKeys.INSTANCE, clock);
        assertThrows(InternalTokenUnavailableException.class,
            () -> verifier.verify(TestKeys.token(first, TestKeys.TARGET, TestKeys.INSTANCE, "GENERAL", NOW)));
    }

    @Test
    void test_businessRuleViolation_expiredCacheDroppedWhenRefreshFails() {
        controller.publish(first);
        keySource.findKey("kid-1");
        controller.fail();
        clock.advance(Duration.ofMinutes(5));

        assertThrows(InternalTokenUnavailableException.class, () -> keySource.findKey("kid-1"));
        controller.recover();
        controller.publish(second);
        assertNull(keySource.findKey("kid-1"));
    }

    @Test
    void test_edgeCase_ignoresNonSigningOrOtherCurveKeys() {
        ECKey encryption = new ECKey.Builder(TestKeys.generate("kid-enc")).keyUse(com.nimbusds.jose.jwk.KeyUse.ENCRYPTION)
            .build();
        controller.publish(first, encryption);

        assertNull(keySource.findKey("kid-enc"));
        assertNotNull(keySource.findKey("kid-1"));
    }

    /** 测试内嵌 JWKS 端点：返回当前配置的公钥集合，或按配置返回 503。 */
    @RestController
    static final class JwksController {

        private final AtomicInteger calls = new AtomicInteger();
        private volatile String body = "{\"keys\":[]}";
        private volatile boolean failing;

        void publish(ECKey... keys) {
            List<JWK> publicKeys = java.util.Arrays.stream(keys).<JWK>map(ECKey::toPublicJWK).toList();
            body = new JWKSet(publicKeys).toString(true);
        }

        void fail() {
            failing = true;
        }

        void recover() {
            failing = false;
        }

        @GetMapping(value = "/jwks", produces = MediaType.APPLICATION_JSON_VALUE)
        String jwks() {
            calls.incrementAndGet();
            if (failing) {
                throw MockHttpFault.raw(503, "{}");
            }
            return body;
        }
    }
}
