package com.paytech.foundation.internalauth.core.token;

import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.regex.Pattern;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.paytech.foundation.internalauth.api.CallerIdentity;
import com.paytech.foundation.internalauth.api.InternalTokenClaims;
import com.paytech.foundation.internalauth.api.InternalTokenInvalidException;
import com.paytech.foundation.internalauth.api.InternalTokenKeySource;
import com.paytech.foundation.internalauth.api.InternalTokenVerifier;

/**
 * ES256 内部调用 Token 校验。
 * <li>只接受 ES256 与合法 {@code kid}；签名、{@code iss}、{@code aud == 本服务}、{@code inst == 本实例} 必须一致。</li>
 * <li>{@code exp} 严格：当前时间不早于 {@code exp} 即拒绝；{@code iat}、{@code nbf} 最多允许超前 60 秒。</li>
 *
 * @author Claude
 */
public final class InternalTokenVerifierImpl implements InternalTokenVerifier {

    /** 签发时间允许的时钟偏差。 */
    public static final Duration CLOCK_SKEW = Duration.ofSeconds(60);

    private static final Pattern KEY_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");

    private static final int MAX_TOKEN_LENGTH = 4096;

    private final Clock clock;
    private final String audience;
    private final String instanceId;
    private final InternalTokenKeySource keySource;

    /**
     * 创建校验器。
     *
     * @param keySource 公钥来源
     * @param audience 本服务名（{@code spring.application.name}）
     * @param instanceId 本实例 ID（{@code paytech.instance-id}）
     * @param clock 时钟
     */
    public InternalTokenVerifierImpl(InternalTokenKeySource keySource, String audience, String instanceId,
        Clock clock) {
        this.keySource = Objects.requireNonNull(keySource, "keySource must not be null");
        this.audience = Objects.requireNonNull(audience, "audience must not be null");
        this.instanceId = Objects.requireNonNull(instanceId, "instanceId must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 依次校验结构、签名、声明与时间。
     */
    @Override
    public CallerIdentity verify(String compactToken) {
        SignedJWT jwt = parse(compactToken);
        verifySignature(jwt, keyOf(jwt.getHeader()));
        JWTClaimsSet rawClaims = claimsOf(jwt);
        InternalTokenClaims claims = InternalTokenClaims.fromJwtClaims(rawClaims);
        if (!audience.equals(claims.audience())) {
            throw new InternalTokenInvalidException("audience mismatch");
        }
        if (!instanceId.equals(claims.instanceId())) {
            throw new InternalTokenInvalidException("instance mismatch");
        }
        verifyTime(claims, rawClaims.getNotBeforeTime());
        return CallerIdentity.verified(claims.subject(), claims.purpose(), claims.jwtId());
    }

    private static SignedJWT parse(String compactToken) {
        if (compactToken == null || compactToken.isEmpty() || compactToken.length() > MAX_TOKEN_LENGTH) {
            throw new InternalTokenInvalidException("token is missing or too long");
        }
        try {
            return SignedJWT.parse(compactToken);
        } catch (ParseException exception) {
            throw new InternalTokenInvalidException("token is not a JWS compact JWT", exception);
        }
    }

    private ECKey keyOf(JWSHeader header) {
        if (!JWSAlgorithm.ES256.equals(header.getAlgorithm())) {
            throw new InternalTokenInvalidException("algorithm must be ES256");
        }
        String keyId = header.getKeyID();
        if (keyId == null || !KEY_ID.matcher(keyId).matches()) {
            throw new InternalTokenInvalidException("kid is missing or malformed");
        }
        ECKey key = keySource.findKey(keyId);
        if (key == null) {
            throw new InternalTokenInvalidException("unknown kid");
        }
        return key;
    }

    private static void verifySignature(SignedJWT jwt, ECKey key) {
        try {
            if (!jwt.verify(new ECDSAVerifier(key))) {
                throw new InternalTokenInvalidException("signature mismatch");
            }
        } catch (JOSEException exception) {
            throw new InternalTokenInvalidException("signature cannot be verified", exception);
        }
    }

    private static JWTClaimsSet claimsOf(SignedJWT jwt) {
        try {
            return jwt.getJWTClaimsSet();
        } catch (ParseException exception) {
            throw new InternalTokenInvalidException("claims are not a JSON object", exception);
        }
    }

    private void verifyTime(InternalTokenClaims claims, Date notBefore) {
        Instant now = clock.instant();
        if (!now.isBefore(claims.expiresAt())) {
            throw new InternalTokenInvalidException("token expired");
        }
        Instant latestAcceptable = now.plus(CLOCK_SKEW);
        if (claims.issuedAt().isAfter(latestAcceptable)) {
            throw new InternalTokenInvalidException("token issued in the future");
        }
        if (notBefore != null && notBefore.toInstant().isAfter(latestAcceptable)) {
            throw new InternalTokenInvalidException("token not yet valid");
        }
    }
}
