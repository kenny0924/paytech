package com.paytech.foundation.internalauth.auto.token;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import org.junit.jupiter.api.Test;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.paytech.foundation.internalauth.api.CallerIdentity;
import com.paytech.foundation.internalauth.api.InternalTokenClaims;
import com.paytech.foundation.internalauth.api.InternalTokenInvalidException;
import com.paytech.foundation.internalauth.core.supplier.StaticKeyInternalTokenSupport;
import com.paytech.foundation.internalauth.core.token.InternalTokenVerifierImpl;
import com.paytech.foundation.internalauth.test.MutableClock;
import com.paytech.foundation.internalauth.test.TestKeys;

/**
 * 内部调用 Token 签发 / 校验往返与各类拒绝：目标、实例、有效期、签名、算法、签发方与声明。
 *
 * @see InternalTokenVerifierImpl#verify(String)
 * @see StaticKeyInternalTokenSupport#token(String, String)
 */
class InternalTokenVerifierAutoTest {

    private static final Instant NOW = Instant.parse("2026-10-17T10:00:00Z");

    private final ECKey key = TestKeys.generate("kid-1");
    private final MutableClock clock = new MutableClock(NOW);
    private final StaticKeyInternalTokenSupport support = new StaticKeyInternalTokenSupport(key, key.toPublicJWK(),
        TestKeys.CALLER, TestKeys.INSTANCE, clock);
    private final InternalTokenVerifierImpl verifier = new InternalTokenVerifierImpl(support, TestKeys.TARGET,
        TestKeys.INSTANCE, clock);

    @Test
    void test_normalFlow_roundTrip() {
        String token = support.token(TestKeys.TARGET, "CHANNEL_CARD_MATERIAL_REVEAL");

        CallerIdentity identity = verifier.verify(token);

        assertEquals(TestKeys.CALLER, identity.caller());
        assertEquals("CHANNEL_CARD_MATERIAL_REVEAL", identity.purpose());
        assertTrue(identity.verified());
        assertTrue(identity.jti().length() >= 32);
    }

    @Test
    void test_normalFlow_validUntilJustBeforeExpiry() {
        String token = support.token(TestKeys.TARGET, InternalTokenClaims.GENERAL_PURPOSE);

        clock.advance(Duration.ofSeconds(299));

        assertEquals(TestKeys.CALLER, verifier.verify(token).caller());
    }

    @Test
    void test_businessRuleViolation_audienceMismatch() {
        String token = support.token("ledger-service", InternalTokenClaims.GENERAL_PURPOSE);

        assertRejected(token, "audience mismatch");
    }

    @Test
    void test_businessRuleViolation_instanceMismatch() {
        String token = TestKeys.token(key, TestKeys.TARGET, "other-instance", "GENERAL", NOW);

        assertRejected(token, "instance mismatch");
    }

    @Test
    void test_businessRuleViolation_expired() {
        String token = support.token(TestKeys.TARGET, InternalTokenClaims.GENERAL_PURPOSE);

        clock.advance(Duration.ofSeconds(300));

        assertRejected(token, "expired");
    }

    @Test
    void test_edgeCase_issuedAtSkew() {
        assertEquals(TestKeys.CALLER,
            verifier.verify(TestKeys.token(key, TestKeys.TARGET, TestKeys.INSTANCE, "GENERAL", NOW.plusSeconds(60)))
                .caller());
        assertRejected(TestKeys.token(key, TestKeys.TARGET, TestKeys.INSTANCE, "GENERAL", NOW.plusSeconds(61)),
            "future");
    }

    @Test
    void test_edgeCase_notBeforeSkew() throws JOSEException {
        JWTClaimsSet claims = new JWTClaimsSet.Builder(standardClaims(NOW, NOW.plusSeconds(300)).toJwtClaims())
            .notBeforeTime(Date.from(NOW.plusSeconds(61))).build();

        assertRejected(sign(key, JWSAlgorithm.ES256, claims), "not yet valid");
    }

    @Test
    void test_businessRuleViolation_lifetimeOverFiveMinutes() throws JOSEException {
        JWTClaimsSet claims = new JWTClaimsSet.Builder(standardClaims(NOW, NOW.plusSeconds(300)).toJwtClaims())
            .expirationTime(Date.from(NOW.plusSeconds(301))).build();

        assertRejected(sign(key, JWSAlgorithm.ES256, claims), "malformed");
        assertThrows(IllegalArgumentException.class, () -> standardClaims(NOW, NOW.plusSeconds(301)));
    }

    @Test
    void test_businessRuleViolation_signatureFromAnotherKeyWithSameKid() {
        String forged = TestKeys.token(TestKeys.generate("kid-1"), TestKeys.TARGET, TestKeys.INSTANCE, "GENERAL", NOW);

        assertRejected(forged, "signature");
    }

    @Test
    void test_businessRuleViolation_unknownKid() {
        String token = TestKeys.token(TestKeys.generate("kid-2"), TestKeys.TARGET, TestKeys.INSTANCE, "GENERAL", NOW);

        assertRejected(token, "unknown kid");
    }

    @Test
    void test_businessRuleViolation_algorithmNotEs256() throws JOSEException {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).keyID("kid-1").build(),
            standardClaims(NOW, NOW.plusSeconds(300)).toJwtClaims());
        jwt.sign(new MACSigner(new byte[32]));

        assertRejected(jwt.serialize(), "ES256");
    }

    @Test
    void test_businessRuleViolation_issuerMismatch() throws JOSEException {
        JWTClaimsSet claims = new JWTClaimsSet.Builder(standardClaims(NOW, NOW.plusSeconds(300)).toJwtClaims())
            .issuer("payment-service").build();

        assertRejected(sign(key, JWSAlgorithm.ES256, claims), "issuer");
    }

    @Test
    void test_invalidParameter_missingPurposeAndMalformedToken() throws JOSEException {
        JWTClaimsSet claims = new JWTClaimsSet.Builder(standardClaims(NOW, NOW.plusSeconds(300)).toJwtClaims())
            .claim(InternalTokenClaims.PURPOSE_CLAIM, null).build();

        assertRejected(sign(key, JWSAlgorithm.ES256, claims), "malformed");
        assertRejected("not-a-token", "JWS");
        assertRejected("", "missing");
        assertRejected(null, "missing");
    }

    private void assertRejected(String token, String reason) {
        InternalTokenInvalidException exception = assertThrows(InternalTokenInvalidException.class,
            () -> verifier.verify(token));
        assertTrue(exception.getMessage().contains(reason), exception.getMessage());
        assertEquals("INTERNAL_TOKEN_INVALID", exception.getCode());
    }

    private static InternalTokenClaims standardClaims(Instant issuedAt, Instant expiresAt) {
        return new InternalTokenClaims(TestKeys.CALLER, TestKeys.TARGET, TestKeys.INSTANCE, "GENERAL", "jti-1",
            issuedAt, expiresAt);
    }

    private static String sign(ECKey key, JWSAlgorithm algorithm, JWTClaimsSet claims) throws JOSEException {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(algorithm).keyID(key.getKeyID()).build(), claims);
        jwt.sign(new ECDSASigner(key));
        return jwt.serialize();
    }
}
