package com.paytech.foundation.internalauth.test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.paytech.foundation.internalauth.api.InternalTokenClaims;
import com.paytech.foundation.internalauth.core.token.InternalTokenSigner;

/**
 * 测试用 ES256 密钥与 Token 构造；只生成合成密钥，不使用任何真实凭据。
 */
public final class TestKeys {

    /** 测试调用方。 */
    public static final String CALLER = "payment-service";

    /** 测试目标服务。 */
    public static final String TARGET = "card-service";

    /** 测试实例。 */
    public static final String INSTANCE = "main";

    private TestKeys() {
    }

    /**
     * 生成 P-256 私钥 JWK。
     *
     * @param keyId kid
     * @return 私钥 JWK
     */
    public static ECKey generate(String keyId) {
        try {
            return new ECKeyGenerator(Curve.P_256).keyID(keyId).keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.ES256)
                .generate();
        } catch (JOSEException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /**
     * 以给定时间签发标准声明的 Token。
     *
     * @param key 私钥
     * @param audience 目标服务
     * @param instance 实例
     * @param purpose Purpose
     * @param issuedAt 签发时间
     * @return Token
     */
    public static String token(ECKey key, String audience, String instance, String purpose, Instant issuedAt) {
        Instant iat = issuedAt.truncatedTo(ChronoUnit.SECONDS);
        return new InternalTokenSigner(key).sign(new InternalTokenClaims(CALLER, audience, instance, purpose,
            "jti-" + System.nanoTime(), iat, iat.plus(InternalTokenClaims.MAX_LIFETIME)));
    }
}
