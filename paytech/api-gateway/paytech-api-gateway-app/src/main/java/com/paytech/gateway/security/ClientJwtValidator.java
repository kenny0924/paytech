package com.paytech.gateway.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;


/** 校验 Access Token 的身份与时间边界，渠道身份留给授权层判定。 */
public final class ClientJwtValidator implements OAuth2TokenValidator<Jwt> {
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);
    private static final Duration MAX_LIFETIME = Duration.ofSeconds(900);

    private final OAuth2TokenValidator<Jwt> delegate;

    public ClientJwtValidator(ClientSecurityProperties properties, Clock clock) {
        JwtTimestampValidator timestampValidator = new JwtTimestampValidator(CLOCK_SKEW);
        timestampValidator.setClock(clock);
        delegate = new DelegatingOAuth2TokenValidator<>(new JwtIssuerValidator(properties.issuer()), timestampValidator,
            new JwtClaimValidator<List<String>>("aud", audience -> List.of(properties.audience()).equals(audience)),
            new JwtClaimValidator<Object>("token_use", "ACCESS"::equals),
            new JwtClaimValidator<Object>("realm", "CUSTOMER"::equals), new JwtClaimValidator<Object>("channel", "CLIENT"::equals),
            new JwtClaimValidator<Object>("actor_type", "PLATFORM_USER"::equals), requiredTextClaim("sub"),
            requiredTextClaim("sid"), requiredTextClaim("jti"), ClientJwtValidator::validateContext,
            jwt -> validateTimeOrdering(jwt, clock));
    }

    private static JwtClaimValidator<Object> requiredTextClaim(String claim) {
        return new JwtClaimValidator<>(claim, ClientJwtValidator::validIdentity);
    }

    private static OAuth2TokenValidatorResult validateContext(Jwt jwt) {
        Object context = jwt.getClaims().get("context_type");
        boolean tenantContext = "TENANT".equals(context) && "CLIENT".equals(jwt.getClaims().get("tenant_type"))
            && validIdentity(jwt.getClaims().get("tenant_id"))
            && validIdentity(jwt.getClaims().get("tenant_member_id"));
        boolean bootstrapContext = "ACCOUNT".equals(context)
            && List.of("tenant_id", "tenant_type", "tenant_member_id").stream().noneMatch(jwt.getClaims()::containsKey);
        if (!tenantContext && !bootstrapContext) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Client 上下文无效", null));
        }
        return OAuth2TokenValidatorResult.success();
    }

    private static boolean validIdentity(Object value) {
        return value instanceof String text && text.matches("[1-9][0-9]{0,18}") && fitsLong(text);
    }

    /** Platform 人类账号和会话采用正数 BIGINT 标识。 */
    private static boolean fitsLong(String text) {
        try { return Long.parseLong(text)>0; } catch(NumberFormatException invalid) { return false; }
    }

    private static OAuth2TokenValidatorResult validateTimeOrdering(Jwt jwt, Clock clock) {
        Instant issuedAt = jwt.getIssuedAt();
        Instant notBefore = jwt.getNotBefore();
        Instant expiresAt = jwt.getExpiresAt();
        if (issuedAt == null || notBefore == null || expiresAt == null || issuedAt.isAfter(notBefore)
            || !notBefore.isBefore(expiresAt) || Duration.between(issuedAt, expiresAt).compareTo(MAX_LIFETIME) > 0
            || issuedAt.isAfter(clock.instant().plus(CLOCK_SKEW))) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Access Token 时间范围无效", null));
        }
        return OAuth2TokenValidatorResult.success();
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        return delegate.validate(token);
    }
}
