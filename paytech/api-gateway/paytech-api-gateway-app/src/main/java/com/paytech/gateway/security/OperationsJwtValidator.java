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

import com.paytech.gateway.guard.policy.RawGuardIdentity;

/** 校验 Access Token 的身份与时间边界，渠道身份留给授权层判定。 */
public final class OperationsJwtValidator implements OAuth2TokenValidator<Jwt> {
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);
    private static final Duration MAX_LIFETIME = Duration.ofSeconds(900);

    private final OAuth2TokenValidator<Jwt> delegate;

    public OperationsJwtValidator(OperationsSecurityProperties properties, Clock clock) {
        JwtTimestampValidator timestampValidator = new JwtTimestampValidator(CLOCK_SKEW);
        timestampValidator.setClock(clock);
        delegate = new DelegatingOAuth2TokenValidator<>(new JwtIssuerValidator(properties.issuer()), timestampValidator,
            new JwtClaimValidator<List<String>>("aud", audience -> List.of(properties.audience()).equals(audience)),
            new JwtClaimValidator<Object>("token_use", "ACCESS"::equals),
            new JwtClaimValidator<Object>("realm", "OPERATIONS"::equals), requiredTextClaim("sub"),
            requiredTextClaim("sid"), requiredTextClaim("jti"), OperationsJwtValidator::validateContext,
            jwt -> validateTimeOrdering(jwt, clock));
    }

    private static JwtClaimValidator<Object> requiredTextClaim(String claim) {
        return new JwtClaimValidator<>(claim, OperationsJwtValidator::validIdentity);
    }

    private static OAuth2TokenValidatorResult validateContext(Jwt jwt) {
        Object context = jwt.getClaims().get("context_type");
        boolean tenantContext = "TENANT".equals(context) && "INTERNAL".equals(jwt.getClaims().get("tenant_type"))
            && validIdentity(jwt.getClaims().get("tenant_id"))
            && validIdentity(jwt.getClaims().get("tenant_member_id"));
        boolean bootstrapContext = "BOOTSTRAP".equals(context)
            && List.of("tenant_id", "tenant_type", "tenant_member_id").stream().noneMatch(jwt.getClaims()::containsKey);
        if (!tenantContext && !bootstrapContext) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Operations 上下文无效", null));
        }
        return OAuth2TokenValidatorResult.success();
    }

    private static boolean validIdentity(Object value) {
        return value instanceof String text && RawGuardIdentity.isValid(text);
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
