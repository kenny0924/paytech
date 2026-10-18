package com.paytech.gateway.test.builder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.*;

import com.nimbusds.jose.Algorithm;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyOperation;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;

/** 构建由测试专用 RSA-2048 Key 签名的 Operations Access JWT。 */
public final class JwtFixtureBuilder {
    public static final String ISSUER = "https://platform-support.local.test";
    public static final String AUDIENCE = "operations-console";
    public static final String SUBJECT = "platform-user-1001";
    public static final String SESSION_ID = "operations-session-2001";
    public static final String TENANT_ID = "internal-tenant-3001";
    public static final String TENANT_MEMBER_ID = "internal-member-4001";
    public static final Instant NOW = Instant.parse("2026-08-29T12:00:00Z");

    private static final RSAKey FIXTURE_KEY = generateFixtureKey();
    private static final RSAPrivateKey WRONG_PRIVATE_KEY = generateWrongPrivateKey();

    private final Map<String, Object> claims = new LinkedHashMap<>();
    private Algorithm algorithm = JWSAlgorithm.RS256;
    private String keyId = "operations-key-" + UUID.randomUUID();
    private boolean wrongSigningKey;

    private JwtFixtureBuilder() {
        claims.put("iss", ISSUER);
        claims.put("sub", SUBJECT);
        claims.put("aud", List.of(AUDIENCE));
        claims.put("token_use", "ACCESS");
        claims.put("realm", "OPERATIONS");
        claims.put("channel", "OPERATIONS");
        claims.put("actor_type", "PLATFORM_USER");
        claims.put("context_type", "TENANT");
        claims.put("tenant_id", TENANT_ID);
        claims.put("tenant_type", "INTERNAL");
        claims.put("tenant_member_id", TENANT_MEMBER_ID);
        claims.put("sid", SESSION_ID);
        claims.put("jti", "access-token-" + UUID.randomUUID());
        claims.put("iat", Date.from(NOW.minusSeconds(60)));
        claims.put("nbf", Date.from(NOW.minusSeconds(60)));
        claims.put("exp", Date.from(NOW.plusSeconds(600)));
    }

    public static JwtFixtureBuilder template() {
        return new JwtFixtureBuilder();
    }

    public static String jwksJson(String keyId) {
        return new JWKSet(publicJwk(keyId)).toString();
    }

    public static String jwksJson(List<String> keyIds) {
        return new JWKSet(keyIds.stream().<com.nimbusds.jose.jwk.JWK>map(JwtFixtureBuilder::publicJwk).toList())
            .toString();
    }

    public static RSAKey publicJwk(String keyId) {
        try {
            return new RSAKey.Builder(FIXTURE_KEY.toRSAPublicKey()).keyID(keyId).keyUse(KeyUse.SIGNATURE)
                .keyOperations(Set.of(KeyOperation.VERIFY)).algorithm(JWSAlgorithm.RS256).build();
        } catch (Exception exception) {
            throw new IllegalStateException("无法构建 JWKS Fixture", exception);
        }
    }

    public static RSAKey privateJwk(String keyId) {
        try {
            return new RSAKey.Builder(FIXTURE_KEY.toRSAPublicKey()).privateKey(FIXTURE_KEY.toRSAPrivateKey())
                .keyID(keyId).keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256).build();
        } catch (Exception exception) {
            throw new IllegalStateException("无法构建私有 JWKS Fixture", exception);
        }
    }

    private static RSAKey generateFixtureKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey)keyPair.getPublic()).privateKey((RSAPrivateKey)keyPair.getPrivate())
                .keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256).build();
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static RSAPrivateKey generateWrongPrivateKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            return (RSAPrivateKey)keyPair.getPrivate();
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    public JwtFixtureBuilder claim(String name, Object value) {
        claims.put(name, value);
        return this;
    }

    public JwtFixtureBuilder withoutClaim(String name) {
        claims.remove(name);
        return this;
    }

    public JwtFixtureBuilder times(Instant issuedAt, Instant notBefore, Instant expiresAt) {
        return claim("iat", Date.from(issuedAt)).claim("nbf", Date.from(notBefore)).claim("exp", Date.from(expiresAt));
    }

    public JwtFixtureBuilder algorithm(Algorithm value) {
        algorithm = value;
        return this;
    }

    public JwtFixtureBuilder withoutKeyId() {
        keyId = null;
        return this;
    }

    public JwtFixtureBuilder keyId(String value) {
        keyId = value;
        return this;
    }

    public JwtFixtureBuilder signWithWrongKey() {
        wrongSigningKey = true;
        return this;
    }

    public String build() {
        try {
            JWTClaimsSet claimSet = buildClaimSet();
            if (Algorithm.NONE.equals(algorithm)) {
                return new PlainJWT(claimSet).serialize();
            }
            JWSHeader.Builder header = new JWSHeader.Builder((JWSAlgorithm)algorithm).type(JOSEObjectType.JWT);
            if (keyId != null) {
                header.keyID(keyId);
            }
            SignedJWT signedJwt = new SignedJWT(header.build(), claimSet);
            RSAPrivateKey signingKey = wrongSigningKey ? WRONG_PRIVATE_KEY : FIXTURE_KEY.toRSAPrivateKey();
            signedJwt.sign(new RSASSASigner(signingKey));
            return signedJwt.serialize();
        } catch (Exception exception) {
            throw new IllegalStateException("无法构建 Operations JWT Fixture", exception);
        }
    }

    private JWTClaimsSet buildClaimSet() {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder();
        claims.forEach(builder::claim);
        return builder.build();
    }

    public String jwksJson() {
        return jwksJson(keyId);
    }
}
