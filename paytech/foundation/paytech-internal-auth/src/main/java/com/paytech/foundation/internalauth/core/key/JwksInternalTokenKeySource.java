package com.paytech.foundation.internalauth.core.key;

import java.io.IOException;
import java.net.URI;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.paytech.foundation.internalauth.api.InternalTokenKeySource;
import com.paytech.foundation.internalauth.api.InternalTokenUnavailableException;

/**
 * 从 Platform JWKS 端点读取 ES256 公钥（{@code internal-auth.jwks-uri}）。
 * <li>按 {@code cacheTtl} 缓存；过期后下一次查找先刷新，刷新失败时丢弃过期公钥并失败关闭。</li>
 * <li>{@code kid} 未命中时强制刷新一次，以接收密钥轮换；两次强制刷新至少间隔 {@code forcedRefreshInterval}，避免伪造
 * {@code kid} 放大对 Platform 的请求。</li>
 * <li>只保留 P-256、用途为签名（或未声明）且算法为 ES256（或未声明）的公钥。</li>
 *
 * @author Claude
 */
public final class JwksInternalTokenKeySource implements InternalTokenKeySource {

    private static final int MAX_JWKS_BYTES = 64 * 1024;

    private final URI jwksUri;
    private final Duration cacheTtl;
    private final Duration timeout;
    private final Duration forcedRefreshInterval;
    private final Clock clock;

    private Map<String, ECKey> keys = Map.of();
    private Instant fetchedAt;
    private Instant lastForcedRefresh;

    /**
     * 创建公钥来源。
     *
     * @param jwksUri JWKS 地址
     * @param cacheTtl 缓存有效期
     * @param timeout 连接与读取超时
     * @param forcedRefreshInterval 两次因 {@code kid} 未命中触发刷新的最小间隔
     * @param clock 时钟
     */
    public JwksInternalTokenKeySource(URI jwksUri, Duration cacheTtl, Duration timeout,
        Duration forcedRefreshInterval, Clock clock) {
        this.jwksUri = Objects.requireNonNull(jwksUri, "jwksUri must not be null");
        this.cacheTtl = Objects.requireNonNull(cacheTtl, "cacheTtl must not be null");
        this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
        this.forcedRefreshInterval = Objects.requireNonNull(forcedRefreshInterval,
            "forcedRefreshInterval must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 先查缓存；缓存过期或 {@code kid} 未命中时刷新。
     */
    @Override
    public synchronized ECKey findKey(String keyId) {
        Instant now = clock.instant();
        if (fetchedAt == null || !now.isBefore(fetchedAt.plus(cacheTtl))) {
            refreshExpired(now);
        }
        ECKey key = keys.get(keyId);
        if (key != null || !forcedRefreshAllowed(now)) {
            return key;
        }
        lastForcedRefresh = now;
        refresh(now);
        return keys.get(keyId);
    }

    private boolean forcedRefreshAllowed(Instant now) {
        return lastForcedRefresh == null || !now.isBefore(lastForcedRefresh.plus(forcedRefreshInterval));
    }

    /** 缓存过期后的刷新：失败时丢弃过期公钥，失败关闭。 */
    private void refreshExpired(Instant now) {
        try {
            refresh(now);
        } catch (InternalTokenUnavailableException exception) {
            keys = Map.of();
            fetchedAt = null;
            throw exception;
        }
    }

    /** 读取并替换缓存；失败时保留现有缓存并抛出不可用。 */
    private void refresh(Instant now) {
        JWKSet set = load();
        Map<String, ECKey> loaded = new HashMap<>();
        for (JWK jwk : set.getKeys()) {
            if (jwk instanceof ECKey ecKey && acceptable(ecKey)) {
                loaded.put(ecKey.getKeyID(), ecKey.toPublicJWK());
            }
        }
        keys = Map.copyOf(loaded);
        fetchedAt = now;
    }

    private JWKSet load() {
        int timeoutMillis = (int) Math.min(timeout.toMillis(), Integer.MAX_VALUE);
        try {
            return JWKSet.load(jwksUri.toURL(), timeoutMillis, timeoutMillis, MAX_JWKS_BYTES);
        } catch (IOException | ParseException | IllegalArgumentException exception) {
            throw new InternalTokenUnavailableException(exception);
        }
    }

    private static boolean acceptable(ECKey key) {
        return key.getKeyID() != null && Curve.P_256.equals(key.getCurve())
            && (key.getKeyUse() == null || KeyUse.SIGNATURE.equals(key.getKeyUse()))
            && (key.getAlgorithm() == null || JWSAlgorithm.ES256.equals(key.getAlgorithm()));
    }
}
