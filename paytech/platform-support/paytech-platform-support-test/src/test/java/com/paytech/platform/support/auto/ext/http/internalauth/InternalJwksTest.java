package com.paytech.platform.support.auto.ext.http.internalauth;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.paytech.foundation.internalauth.core.key.JwksInternalTokenKeySource;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.platform.support.api.internalauth.InternalJwksQueryApi;
import com.paytech.platform.support.test.base.MockTestBase;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.util.InternalTokens;

/**
 * 内部调用 Token JWKS：只发布 P-256 / ES256 公钥，签名公钥在前、轮换中公钥在后；Foundation 公钥来源可直接读取。
 *
 * @see InternalJwksQueryApi#queryInternalJwks()
 */
class InternalJwksTest extends MockTestBase {

    @Autowired
    InternalTokens tokens;

    /** 标准 JWKS：两把公钥、成员固定、无私钥成员。 */
    @Test
    void test_normalFlow() throws Exception {
        String requestId = "plt-jwks-" + UUID.randomUUID();
        var checker = new PlatformResponseChecker(httpTestInvoker.get(InternalTokens.JWKS_PATH,
            TestRequestHeaders.internal(requestId)), objectMapper, requestId).assertOk();

        assertFalse(checker.text().contains("\"d\""), "JWKS 不得包含私钥成员");
        var keys = JWKSet.parse(checker.text()).getKeys();
        assertEquals(List.of("platform-internal-test-1", "platform-internal-test-0"),
            keys.stream().map(key -> key.getKeyID()).toList());
        for (var key : keys) {
            ECKey ec = key.toECKey();
            assertAll(() -> assertEquals("EC", ec.getKeyType().getValue()),
                () -> assertEquals("P-256", ec.getCurve().getName()),
                () -> assertEquals("ES256", ec.getAlgorithm().getName()),
                () -> assertEquals("sig", ec.getKeyUse().identifier()),
                () -> assertFalse(ec.isPrivate()));
        }
    }

    /** 调用方的 Foundation 公钥来源按 kid 读取当前与轮换中公钥，未知 kid 返回空。 */
    @Test
    void test_foundationKeySourceReadsEndpoint() {
        var source = new JwksInternalTokenKeySource(tokens.jwksUri(), Duration.ofMinutes(5), Duration.ofSeconds(2),
            Duration.ZERO, Clock.systemUTC());

        assertNotNull(source.findKey("platform-internal-test-1"));
        assertNotNull(source.findKey("platform-internal-test-0"));
        assertNull(source.findKey("platform-internal-unknown"));
    }
}
