package com.paytech.foundation.internalauth.core.supplier;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.paytech.foundation.internalauth.api.InternalTokenClaims;
import com.paytech.foundation.internalauth.api.InternalTokenSupplier;
import com.paytech.foundation.internalauth.api.InternalTokenUnavailableException;
import com.paytech.foundation.internalauth.contract.InternalTokenIssueApi;
import com.paytech.foundation.internalauth.contract.InternalTokenIssueRequest;
import com.paytech.foundation.internalauth.contract.InternalTokenIssueResponse;

/**
 * 向 Platform 申请内部调用 Token，按 {@code (target, purpose)} 缓存到过期前 60 秒。
 * <li>获取失败、响应缺字段或已临近过期都抛 {@link InternalTokenUnavailableException}，调用方失败关闭。</li>
 *
 * @author Claude
 */
public final class PlatformInternalTokenSupplier implements InternalTokenSupplier {

    /** 过期前提前刷新的余量。 */
    public static final Duration REFRESH_MARGIN = Duration.ofSeconds(60);

    private final Clock clock;
    private final InternalTokenIssueApi issueApi;
    private final Map<CacheKey, CachedToken> cache = new ConcurrentHashMap<>();

    /**
     * 创建 Token 来源。
     *
     * @param issueApi 不注入内部 Token 的 Platform 签发客户端
     * @param clock 时钟
     */
    public PlatformInternalTokenSupplier(InternalTokenIssueApi issueApi, Clock clock) {
        this.issueApi = Objects.requireNonNull(issueApi, "issueApi must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 命中未临近过期的缓存时直接返回，否则同步向 Platform 申请。
     */
    @Override
    public String token(String targetService, String purpose) {
        CacheKey key = new CacheKey(require(targetService, "targetService", true), require(purpose, "purpose", false));
        CachedToken cached = cache.get(key);
        Instant now = clock.instant();
        if (cached != null && now.isBefore(cached.refreshAt())) {
            return cached.token();
        }
        CachedToken issued = issue(key, now);
        cache.put(key, issued);
        return issued.token();
    }

    private CachedToken issue(CacheKey key, Instant now) {
        InternalTokenIssueResponse response;
        try {
            response = issueApi.issue(new InternalTokenIssueRequest(key.targetService(), key.purpose()));
        } catch (RuntimeException exception) {
            throw new InternalTokenUnavailableException(exception);
        }
        if (response == null || response.getToken() == null || response.getToken().isBlank()
            || response.getExpiresAt() == null) {
            throw new InternalTokenUnavailableException(null);
        }
        Instant refreshAt = expiresAt(response.getExpiresAt()).minus(REFRESH_MARGIN);
        if (!now.isBefore(refreshAt)) {
            throw new InternalTokenUnavailableException(null);
        }
        return new CachedToken(response.getToken(), refreshAt);
    }

    private static Instant expiresAt(String text) {
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException exception) {
            throw new InternalTokenUnavailableException(exception);
        }
    }

    private static String require(String value, String name, boolean service) {
        java.util.regex.Pattern pattern = service ? InternalTokenClaims.SERVICE_NAME : InternalTokenClaims.PURPOSE;
        if (value == null || !pattern.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " has an invalid format");
        }
        return value;
    }

    private record CacheKey(String targetService, String purpose) {
    }

    private record CachedToken(String token, Instant refreshAt) {
    }
}
