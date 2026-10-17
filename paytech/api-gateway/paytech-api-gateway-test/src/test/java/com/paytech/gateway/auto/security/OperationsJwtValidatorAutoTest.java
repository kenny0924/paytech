package com.paytech.gateway.auto.security;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.oauth2.jwt.Jwt;

import com.paytech.gateway.security.OperationsJwtValidator;
import com.paytech.gateway.security.OperationsSecurityProperties;
import com.paytech.gateway.test.builder.JwtFixtureBuilder;

/** Operations Access JWT Realm、上下文与 Tenant 的纯校验矩阵。 */
class OperationsJwtValidatorAutoTest {

    private static OperationsJwtValidator validator() {
        OperationsSecurityProperties properties =
            new OperationsSecurityProperties(JwtFixtureBuilder.ISSUER, JwtFixtureBuilder.AUDIENCE);
        return new OperationsJwtValidator(properties, Clock.fixed(JwtFixtureBuilder.NOW, ZoneOffset.UTC));
    }

    private static Jwt.Builder validTokenBuilder() {
        return Jwt.withTokenValue("operations-access-token").header("alg", "RS256").issuer(JwtFixtureBuilder.ISSUER)
            .subject(JwtFixtureBuilder.SUBJECT).audience(List.of(JwtFixtureBuilder.AUDIENCE))
            .issuedAt(JwtFixtureBuilder.NOW.minusSeconds(60)).notBefore(JwtFixtureBuilder.NOW.minusSeconds(60))
            .expiresAt(JwtFixtureBuilder.NOW.plusSeconds(600)).claim("token_use", "ACCESS")
            .claim("sid", JwtFixtureBuilder.SESSION_ID).claim("jti", "access-token-1").claim("context_type", "TENANT")
            .claim("tenant_id", JwtFixtureBuilder.TENANT_ID).claim("tenant_type", "INTERNAL")
            .claim("tenant_member_id", JwtFixtureBuilder.TENANT_MEMBER_ID);
    }

    private static Stream<Arguments> invalidContexts() {
        return Stream.of(Arguments.of("missing context", "context_type", null),
            Arguments.of("account context is not Operations", "context_type", "ACCOUNT"),
            Arguments.of("unknown context", "context_type", "FUTURE"),
            Arguments.of("structured context", "context_type", Map.of("value", "TENANT")),
            Arguments.of("bootstrap cannot inherit tenant", "context_type", "BOOTSTRAP"),
            Arguments.of("missing tenant", "tenant_id", null), Arguments.of("missing tenant type", "tenant_type", null),
            Arguments.of("missing member", "tenant_member_id", null),
            Arguments.of("client tenant", "tenant_type", "CLIENT"),
            Arguments.of("partner tenant", "tenant_type", "PARTNER"), Arguments.of("numeric tenant", "tenant_id", 3001),
            Arguments.of("blank tenant", "tenant_id", " "),
            Arguments.of("oversized member", "tenant_member_id", "m".repeat(513)),
            Arguments.of("control member", "tenant_member_id", "member\nvalue"),
            Arguments.of("control session", "sid", "session\rvalue"), Arguments.of("numeric jti", "jti", 2001));
    }

    private static Stream<Arguments> invalidRealms() {
        return Stream.of(Arguments.of("missing realm", null), Arguments.of("empty realm", ""),
            Arguments.of("wrong realm", "CUSTOMER"), Arguments.of("numeric realm", 1001),
            Arguments.of("structured realm", Map.of("value", "OPERATIONS")));
    }

    @Test
    void test_operationsRealmIsAccepted() {
        Jwt token = validTokenBuilder().claim("realm", "OPERATIONS").build();

        Assertions.assertFalse(validator().validate(token).hasErrors());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRealms")
    void test_invalidRealmIsRejected(String ignored, Object realm) {
        Jwt.Builder builder = validTokenBuilder();
        if (realm != null) {
            builder.claim("realm", realm);
        }

        Assertions.assertTrue(validator().validate(builder.build()).hasErrors());
    }

    @Test
    void test_bootstrapContextDoesNotCarryTenantClaims() {
        Jwt token = validTokenBuilder().claim("realm", "OPERATIONS").claim("context_type", "BOOTSTRAP")
            .claims(claims -> List.of("tenant_id", "tenant_type", "tenant_member_id").forEach(claims::remove)).build();

        Assertions.assertFalse(validator().validate(token).hasErrors());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidContexts")
    void test_invalidContextMatrixIsRejected(String ignored, String claim, Object value) {
        Jwt.Builder builder = validTokenBuilder().claim("realm", "OPERATIONS");
        if (value == null) {
            builder.claims(claims -> claims.remove(claim));
        } else {
            builder.claim(claim, value);
        }

        Assertions.assertTrue(validator().validate(builder.build()).hasErrors());
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"tenant_id", "tenant_type", "tenant_member_id"})
    void test_bootstrapRejectsEveryTenantClaim(String claim) {
        Jwt token = validTokenBuilder().claim("realm", "OPERATIONS").claim("context_type", "BOOTSTRAP")
            .claims(claims -> List.of("tenant_id", "tenant_type", "tenant_member_id").forEach(claims::remove))
            .claim(claim, "untrusted-tenant-value").build();

        Assertions.assertTrue(validator().validate(token).hasErrors());
    }

    @Test
    void test_audienceCannotAlsoAuthorizeAnotherChannel() {
        Jwt token = validTokenBuilder().claim("realm", "OPERATIONS")
            .audience(List.of(JwtFixtureBuilder.AUDIENCE, "client-console")).build();

        Assertions.assertTrue(validator().validate(token).hasErrors());
    }
}
