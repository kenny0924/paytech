package com.paytech.foundation.internalauth.core.supplier;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

import com.nimbusds.jose.jwk.ECKey;
import com.paytech.foundation.internalauth.api.InternalTokenClaims;
import com.paytech.foundation.internalauth.api.InternalTokenKeySource;
import com.paytech.foundation.internalauth.api.InternalTokenSupplier;
import com.paytech.foundation.internalauth.core.token.InternalTokenSigner;

/**
 * 仅限本地 / mock：用配置的 ES256 密钥对在本进程签发并校验内部调用 Token，替代 Platform 签发与 JWKS。
 * <li>自动配置只在 {@code internal-auth.mode != OFF} 且配置了 {@code internal-auth.static-key.*} 时创建，并拒绝 prod Profile。</li>
 * <li>签发的 Token 与 Platform 签发的声明完全相同：{@code iss} 固定、有效期 300 秒。</li>
 *
 * @author Claude
 */
public final class StaticKeyInternalTokenSupport implements InternalTokenSupplier, InternalTokenKeySource {

    private final Clock clock;
    private final String callerService;
    private final String instanceId;
    private final ECKey publicKey;
    private final InternalTokenSigner signer;

    /**
     * 创建本地密钥对支持。
     *
     * @param privateKey 含私钥与 {@code kid} 的 P-256 JWK
     * @param publicKey 对应公钥 JWK，{@code kid} 与坐标必须一致
     * @param callerService 本服务名，作为签发 Token 的 {@code sub}
     * @param instanceId 实例 ID
     * @param clock 时钟
     * @throws IllegalArgumentException 密钥对不匹配
     */
    public StaticKeyInternalTokenSupport(ECKey privateKey, ECKey publicKey, String callerService, String instanceId,
        Clock clock) {
        this.signer = new InternalTokenSigner(privateKey);
        this.publicKey = Objects.requireNonNull(publicKey, "publicKey must not be null").toPublicJWK();
        if (!this.publicKey.equals(signer.publicKey())) {
            throw new IllegalArgumentException("internal-auth.static-key public-jwk does not match private-jwk");
        }
        this.callerService = Objects.requireNonNull(callerService, "callerService must not be null");
        this.instanceId = Objects.requireNonNull(instanceId, "instanceId must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 每次签发新 Token；本地签名无需缓存。
     */
    @Override
    public String token(String targetService, String purpose) {
        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        InternalTokenClaims claims = new InternalTokenClaims(callerService, targetService, instanceId, purpose,
            UUID.randomUUID().toString(), issuedAt, issuedAt.plus(InternalTokenClaims.MAX_LIFETIME));
        return signer.sign(claims);
    }

    /**
     * 只认配置的唯一公钥。
     */
    @Override
    public ECKey findKey(String keyId) {
        return publicKey.getKeyID().equals(keyId) ? publicKey : null;
    }
}
