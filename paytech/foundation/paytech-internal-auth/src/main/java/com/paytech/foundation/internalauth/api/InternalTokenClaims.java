package com.paytech.foundation.internalauth.api;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import com.nimbusds.jwt.JWTClaimsSet;

/**
 * 内部调用 Token 的声明集合（D26），签名算法固定 ES256，格式 JWS Compact。
 * <li>{@code iss} 固定 {@value #ISSUER}；{@code sub} 为调用方 {@code spring.application.name}；{@code aud} 为唯一目标服务名。</li>
 * <li>{@code inst} 为实例 ID；{@code purpose} 为大写 Code；{@code jti} 唯一；{@code exp - iat} 不超过 300 秒。</li>
 *
 * @author Claude
 */
public final class InternalTokenClaims {

    /** 唯一签发方。 */
    public static final String ISSUER = "platform-support-service";

    /** 默认 Purpose。 */
    public static final String GENERAL_PURPOSE = "GENERAL";

    /** 最长有效期。 */
    public static final Duration MAX_LIFETIME = Duration.ofSeconds(300);

    /** 实例 ID 声明名。 */
    public static final String INSTANCE_CLAIM = "inst";

    /** Purpose 声明名。 */
    public static final String PURPOSE_CLAIM = "purpose";

    /** Purpose 格式。 */
    public static final Pattern PURPOSE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

    /** 服务名格式（{@code sub}、{@code aud}）。 */
    public static final Pattern SERVICE_NAME = Pattern.compile("[a-z][a-z0-9-]{0,63}");

    /** 实例 ID 格式。 */
    public static final Pattern INSTANCE_ID = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");

    /** Token ID 格式。 */
    public static final Pattern JWT_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");

    private final String subject;
    private final String audience;
    private final String instanceId;
    private final String purpose;
    private final String jwtId;
    private final Instant issuedAt;
    private final Instant expiresAt;

    /**
     * 创建并校验声明。
     *
     * @param subject 调用方服务名
     * @param audience 目标服务名
     * @param instanceId 实例 ID
     * @param purpose Purpose Code
     * @param jwtId Token ID
     * @param issuedAt 签发时间（秒精度）
     * @param expiresAt 过期时间（秒精度）
     * @throws IllegalArgumentException 任一声明非法或有效期超过 300 秒
     */
    public InternalTokenClaims(String subject, String audience, String instanceId, String purpose, String jwtId,
        Instant issuedAt, Instant expiresAt) {
        this.subject = require(subject, SERVICE_NAME, "sub");
        this.audience = require(audience, SERVICE_NAME, "aud");
        this.instanceId = require(instanceId, INSTANCE_ID, "inst");
        this.purpose = require(purpose, PURPOSE, "purpose");
        this.jwtId = require(jwtId, JWT_ID, "jti");
        this.issuedAt = Objects.requireNonNull(issuedAt, "iat must not be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "exp must not be null");
        Duration lifetime = Duration.between(issuedAt, expiresAt);
        if (lifetime.isNegative() || lifetime.isZero() || lifetime.compareTo(MAX_LIFETIME) > 0) {
            throw new IllegalArgumentException("token lifetime must be within (0, 300] seconds");
        }
    }

    /**
     * 从已验签的 JWT 声明解析；任何缺失或格式不符都视为 Token 不合法。
     *
     * @param claims Nimbus 声明集合
     * @return 声明
     * @throws InternalTokenInvalidException 声明缺失、格式非法或签发方不符
     */
    public static InternalTokenClaims fromJwtClaims(JWTClaimsSet claims) {
        if (!ISSUER.equals(claims.getIssuer())) {
            throw new InternalTokenInvalidException("issuer mismatch");
        }
        List<String> audience = claims.getAudience();
        if (audience == null || audience.size() != 1) {
            throw new InternalTokenInvalidException("audience must contain exactly one service");
        }
        try {
            return new InternalTokenClaims(claims.getSubject(), audience.getFirst(),
                stringClaim(claims, INSTANCE_CLAIM), stringClaim(claims, PURPOSE_CLAIM), claims.getJWTID(),
                instant(claims.getIssueTime(), "iat"), instant(claims.getExpirationTime(), "exp"));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new InternalTokenInvalidException("claims are malformed", exception);
        }
    }

    /**
     * 转为待签名的 JWT 声明。
     *
     * @return Nimbus 声明集合
     */
    public JWTClaimsSet toJwtClaims() {
        return new JWTClaimsSet.Builder()
            .issuer(ISSUER)
            .subject(subject)
            .audience(audience)
            .claim(INSTANCE_CLAIM, instanceId)
            .claim(PURPOSE_CLAIM, purpose)
            .jwtID(jwtId)
            .issueTime(Date.from(issuedAt))
            .expirationTime(Date.from(expiresAt))
            .build();
    }

    /**
     * 调用方服务名。
     *
     * @return {@code sub}
     */
    public String subject() {
        return subject;
    }

    /**
     * 目标服务名。
     *
     * @return {@code aud}
     */
    public String audience() {
        return audience;
    }

    /**
     * 实例 ID。
     *
     * @return {@code inst}
     */
    public String instanceId() {
        return instanceId;
    }

    /**
     * Purpose Code。
     *
     * @return {@code purpose}
     */
    public String purpose() {
        return purpose;
    }

    /**
     * Token ID。
     *
     * @return {@code jti}
     */
    public String jwtId() {
        return jwtId;
    }

    /**
     * 签发时间。
     *
     * @return {@code iat}
     */
    public Instant issuedAt() {
        return issuedAt;
    }

    /**
     * 过期时间。
     *
     * @return {@code exp}
     */
    public Instant expiresAt() {
        return expiresAt;
    }

    private static String stringClaim(JWTClaimsSet claims, String name) {
        Object value = claims.getClaim(name);
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException(name + " must be a string");
        }
        return text;
    }

    private static Instant instant(Date value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.toInstant();
    }

    private static String require(String value, Pattern pattern, String name) {
        if (value == null || !pattern.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " has an invalid format");
        }
        return value;
    }
}
