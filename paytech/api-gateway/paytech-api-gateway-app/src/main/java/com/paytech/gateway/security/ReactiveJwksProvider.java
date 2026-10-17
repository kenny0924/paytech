package com.paytech.gateway.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.KeyOperation;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.SignedJWT;
import com.paytech.gateway.observability.GatewayMetrics;
import com.paytech.platform.support.api.authentication.response.JsonWebKeyResponseBody;
import com.paytech.platform.support.api.authentication.response.JsonWebKeySetResponseBody;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** 提供有界、可离线命中的 Operations RSA JWKS 选择能力。 */
@Component
public final class ReactiveJwksProvider {
    private final String jwksPath;
    private static final Set<String> JWKS_FIELDS = Set.of("kty", "kid", "use", "key_ops", "alg", "n", "e");
    private static final int MAX_KEYS = 16;
    private static final int MAX_KID_LENGTH = 128;
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final Duration REFRESH_COOLDOWN = Duration.ofSeconds(5);
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(3);
    private static final ObjectMapper STRICT_JSON_READER =
        JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY, DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private final Clock clock;
    private final Scheduler scheduler;
    private final GatewayMetrics gatewayMetrics;
    private final WebClient webClient;
    private final AtomicReference<Instant> timeHighWater = new AtomicReference<>(Instant.MIN);

    private volatile CachedJwks cachedJwks;
    private RefreshFlight inFlightRefresh;
    private RefreshCooldown refreshCooldown;

    @org.springframework.beans.factory.annotation.Autowired
    public ReactiveJwksProvider(@Qualifier("operationsJwksWebClient") WebClient operationsJwksWebClient,
        Clock operationsJwtClock, @Qualifier("operationsJwksScheduler") Scheduler operationsJwksScheduler,
        GatewayMetrics gatewayMetrics) {
        this(operationsJwksWebClient, operationsJwtClock, operationsJwksScheduler, gatewayMetrics, "OPERATIONS");
    }

    /** 每个 Realm 独立实例持有自己的缓存、冷却与在途刷新；路径不能来自 JWT。 */
    public ReactiveJwksProvider(WebClient operationsJwksWebClient, Clock operationsJwtClock,
        Scheduler operationsJwksScheduler, GatewayMetrics gatewayMetrics, String realm) {
        if (!Set.of("OPERATIONS", "CUSTOMER").contains(realm)) {
            throw new IllegalArgumentException("未知 JWKS Realm");
        }
        this.jwksPath = "/platform-support/authentication/v1/realms/" + realm + "/jwks";
        this.clock = Objects.requireNonNull(operationsJwtClock, "Operations JWT Clock 不可为空");
        this.scheduler = Objects.requireNonNull(operationsJwksScheduler, "Operations JWKS Scheduler 不可为空");
        this.gatewayMetrics = Objects.requireNonNull(gatewayMetrics, "Gateway Metrics 不可为空");
        this.webClient = Objects.requireNonNull(operationsJwksWebClient, "Operations JWKS WebClient 不可为空");
    }

    private static boolean isValidKid(String kid) {
        if (kid == null || kid.isEmpty() || kid.length() > MAX_KID_LENGTH) {
            return false;
        }
        for (int index = 0; index < kid.length(); index++) {
            char character = kid.charAt(index);
            if (!isAsciiLetterOrDigit(character) && character != '.' && character != '_' && character != ':'
                && character != '-') {
                return false;
            }
        }
        return true;
    }

    private static boolean isAsciiLetterOrDigit(char character) {
        return character >= 'A' && character <= 'Z' || character >= 'a' && character <= 'z'
            || character >= '0' && character <= '9';
    }

    public Flux<JWK> select(SignedJWT signedJwt) {
        Objects.requireNonNull(signedJwt, "Operations JWT 不可为空");
        String kid = signedJwt.getHeader().getKeyID();
        if (!JWSAlgorithm.RS256.equals(signedJwt.getHeader().getAlgorithm()) || !isValidKid(kid)) {
            return Flux.empty();
        }
        JWK cachedKey = selectFreshKey(kid);
        if (cachedKey != null) {
            return Flux.just(cachedKey);
        }
        return selectAfterCacheMiss(kid).flatMapMany(refreshed -> Flux.fromIterable(selectKid(refreshed, kid)));
    }

    private synchronized Mono<CachedJwks> selectAfterCacheMiss(String kid) {
        JWK cachedKey = selectFreshKey(kid);
        if (cachedKey != null) {
            return Mono.just(cachedJwks);
        }
        RefreshCooldown cooldown = activeCooldown();
        if (cooldown != null) {
            gatewayMetrics.recordJwksRefreshSuppressed();
            return cooldown.failure() == null ? Mono.just(cachedOrEmpty()) : Mono.error(cooldown.failure());
        }
        if (inFlightRefresh == null) {
            inFlightRefresh = new RefreshFlight();
        }
        return inFlightRefresh.refresh;
    }

    private JWK selectFreshKey(String kid) {
        CachedJwks current = cachedJwks;
        Instant now = monotonicNow();
        return current != null && current.isFresh(now) ? current.keys().get(kid) : null;
    }

    private CachedJwks cachedOrEmpty() {
        CachedJwks current = cachedJwks;
        if (current != null && current.isFresh(monotonicNow())) {
            return current;
        }
        return CachedJwks.empty(monotonicNow());
    }

    private RefreshCooldown activeCooldown() {
        if (refreshCooldown == null) {
            return null;
        }
        if (refreshCooldown.retryAt().isAfter(monotonicNow())) {
            return refreshCooldown;
        }
        refreshCooldown = null;
        return null;
    }

    private void completeRefresh(RefreshFlight flight, CachedJwks refreshed) {
        synchronized (this) {
            if (inFlightRefresh != flight) {
                return;
            }
            Instant completedAt = monotonicNow();
            cachedJwks = refreshed.withExpiry(completedAt.plus(CACHE_TTL));
            refreshCooldown = new RefreshCooldown(completedAt.plus(REFRESH_COOLDOWN), null);
            inFlightRefresh = null;
        }
        recordRefreshMetricSafely(gatewayMetrics::recordJwksRefreshSuccess);
    }

    private void failRefresh(RefreshFlight flight, Throwable throwable) {
        JwksUnavailableException failure = throwable instanceof JwksUnavailableException unavailable ? unavailable
            : new JwksUnavailableException("Operations JWKS 暂时不可用");
        synchronized (this) {
            if (inFlightRefresh != flight) {
                return;
            }
            refreshCooldown = new RefreshCooldown(monotonicNow().plus(REFRESH_COOLDOWN), failure);
            inFlightRefresh = null;
        }
        recordRefreshMetricSafely(gatewayMetrics::recordJwksRefreshFailure);
    }

    private void recordRefreshMetricSafely(Runnable callback) {
        try {
            callback.run();
        } catch (RuntimeException ignored) {
            // Observability must not change the shared refresh result or retain its completed flight.
        }
    }

    private Mono<CachedJwks> fetch() {
        return webClient.get().uri(jwksPath).exchangeToMono(response -> {
            if (response.statusCode().value() == 200) {
                return response.bodyToMono(String.class);
            }
            return response.releaseBody().then(Mono.error(new JwksUnavailableException("Operations JWKS 暂时不可用")));
        }).switchIfEmpty(Mono.error(new JwksUnavailableException("Operations JWKS 响应体为空"))).map(this::parse)
            .timeout(RESPONSE_TIMEOUT, scheduler).onErrorMap(exception -> exception instanceof JwksUnavailableException
                ? exception : new JwksUnavailableException("Operations JWKS 暂时不可用", exception));
    }

    /** 严格解析完整 JWKS，只有全部密钥验证通过才形成缓存结果。 */
    private CachedJwks parse(String payload) {
        try {
            JsonNode rawKeys = strictRawKeys(payload);
            List<JsonWebKeyResponseBody> parsedKeys = new ArrayList<>();
            rawKeys.forEach(rawKey -> parsedKeys.add(toJsonWebKey(rawKey)));
            JsonWebKeySetResponseBody keySet = new JsonWebKeySetResponseBody(parsedKeys);
            Map<String, JWK> keys = new LinkedHashMap<>();
            for (JsonWebKeyResponseBody parsedKey : keySet.getKeys()) {
                JWK key = toRsaKey(parsedKey);
                validateKey(key);
                if (keys.put(key.getKeyID(), key) != null) {
                    throw new JwksUnavailableException("Operations JWKS 存在重复 kid");
                }
            }
            return new CachedJwks(Map.copyOf(keys), Instant.MAX);
        } catch (JwksUnavailableException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new JwksUnavailableException("Operations JWKS 无法解析", exception);
        }
    }

    private JsonNode strictRawKeys(String payload) throws JacksonException {
        JsonNode document = STRICT_JSON_READER.readTree(payload);
        if (document == null || !document.isObject() || document.size() != 1) {
            throw new JwksUnavailableException("Operations JWKS 顶层对象不合法");
        }
        JsonNode rawKeys = document.get("keys");
        if (rawKeys == null || !rawKeys.isArray() || rawKeys.isEmpty() || rawKeys.size() > MAX_KEYS) {
            throw new JwksUnavailableException("Operations JWKS Key 数量不合法");
        }
        for (JsonNode rawKey : rawKeys) {
            if (!rawKey.isObject() || rawKey.size() != JWKS_FIELDS.size()
                || !JWKS_FIELDS.stream().allMatch(rawKey::has)) {
                throw new JwksUnavailableException("Operations JWKS 包含无效 Key 对象");
            }
        }
        return rawKeys;
    }

    /** 将已检查的公开字段映射到共享 JWKS 响应类型。 */
    private JsonWebKeyResponseBody toJsonWebKey(JsonNode rawKey) {
        JsonNode operations = rawKey.get("key_ops");
        if (!operations.isArray()) {
            throw new JwksUnavailableException("Operations JWKS key_ops 不合法");
        }
        List<String> keyOperations = new ArrayList<>();
        operations.forEach(operation -> {
            if (!operation.isTextual()) {
                throw new JwksUnavailableException("Operations JWKS key_ops 不合法");
            }
            keyOperations.add(operation.textValue());
        });
        return new JsonWebKeyResponseBody(requiredText(rawKey, "kty"), requiredText(rawKey, "kid"), requiredText(rawKey, "use"),
            keyOperations, requiredText(rawKey, "alg"), requiredText(rawKey, "n"), requiredText(rawKey, "e"));
    }

    private String requiredText(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || !value.isTextual()) {
            throw new JwksUnavailableException("Operations JWKS 字段不合法: " + field);
        }
        return value.textValue();
    }

    /** 再次校验算法与公开整数，只构造 RSA 验签密钥。 */
    private RSAKey toRsaKey(JsonWebKeyResponseBody key) {
        if (!"RSA".equals(key.getKty()) || !"sig".equals(key.getUse()) || !List.of("verify").equals(key.getKeyOperations())
            || !"RS256".equals(key.getAlg())) {
            throw new JwksUnavailableException("Operations JWKS 包含不受支持的签名 Key");
        }
        Base64URL modulus = new Base64URL(key.getN());
        Base64URL exponent = new Base64URL(key.getE());
        if (!isValidBase64UrlInteger(modulus) || !isValidBase64UrlInteger(exponent)) {
            throw new JwksUnavailableException("Operations JWKS RSA 参数不合法");
        }
        return new RSAKey.Builder(modulus, exponent).keyID(key.getKid()).keyUse(KeyUse.SIGNATURE)
            .keyOperations(Set.of(KeyOperation.VERIFY)).algorithm(JWSAlgorithm.RS256).build();
    }

    private void validateKey(JWK key) {
        if (!(key instanceof RSAKey rsaKey) || rsaKey.isPrivate() || rsaKey.size() < 2048 || !isValidKid(key.getKeyID())
            || !isValidBase64UrlInteger(rsaKey.getModulus()) || !isValidBase64UrlInteger(rsaKey.getPublicExponent())) {
            throw new JwksUnavailableException("Operations JWKS 包含不受支持的签名 Key");
        }
    }

    private boolean isValidBase64UrlInteger(Base64URL value) {
        if (value == null || value.toString().isEmpty()) {
            return false;
        }
        String encoded = value.toString();
        for (int index = 0; index < encoded.length(); index++) {
            char character = encoded.charAt(index);
            if (!isAsciiLetterOrDigit(character) && character != '-' && character != '_') {
                return false;
            }
        }
        byte[] decoded = value.decode();
        return decoded.length > 0 && decoded[0] != 0 && Base64URL.encode(decoded).equals(value);
    }

    private Collection<JWK> selectKid(CachedJwks jwks, String kid) {
        JWK key = jwks.keys().get(kid);
        return key == null ? List.of() : List.of(key);
    }

    private Instant monotonicNow() {
        Instant observed = clock.instant();
        return timeHighWater.accumulateAndGet(observed,
            (current, candidate) -> current.isAfter(candidate) ? current : candidate);
    }

    private record CachedJwks(Map<String, JWK> keys, Instant expiresAt) {
        private CachedJwks {
            Objects.requireNonNull(keys, "keys must not be null");
            Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        }

        private static CachedJwks empty(Instant now) {
            return new CachedJwks(Map.of(), now);
        }

        private CachedJwks withExpiry(Instant expiry) {
            return new CachedJwks(keys, expiry);
        }

        private boolean isFresh(Instant now) {
            return expiresAt.isAfter(now);
        }
    }

    private record RefreshCooldown(Instant retryAt, JwksUnavailableException failure) {
    }

    private final class RefreshFlight {
        private final Mono<CachedJwks> refresh;

        private RefreshFlight() {
            refresh = fetch().doOnNext(refreshed -> completeRefresh(this, refreshed))
                .doOnError(throwable -> failRefresh(this, throwable)).cache();
        }
    }
}
