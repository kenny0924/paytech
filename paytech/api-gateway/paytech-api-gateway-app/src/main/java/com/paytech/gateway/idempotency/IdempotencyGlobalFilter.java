package com.paytech.gateway.idempotency;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.paytech.gateway.idempotency.IdempotencyKeyPolicy.KeyResolution;
import com.paytech.gateway.idempotency.IdempotencyRecordStore.Claim;
import com.paytech.gateway.idempotency.IdempotencyRecordStore.Lease;
import com.paytech.gateway.idempotency.IdempotencyRecordStore.Scope;
import com.paytech.gateway.idempotency.IdempotencyRecordStore.StoredResponse;
import com.paytech.gateway.idempotency.IdempotencyResponseCapture.ReleaseReason;
import com.paytech.gateway.observability.GatewayOutcome;
import com.paytech.gateway.routing.runtime.GatewayRuntimeRoute;
import com.paytech.gateway.security.clientapi.ClientApiIdentity;
import com.paytech.gateway.security.identity.GatewayAuthenticatedIdentity;
import com.paytech.gateway.web.GatewayErrorCode;
import com.paytech.gateway.web.GatewayErrorWriter;
import com.paytech.gateway.web.GatewayExchangeAttributes;
import com.paytech.gateway.web.GatewayGlobalFilterOrder;

import reactor.core.publisher.Mono;

/**
 * 对 idempotencyMode=REQUIRED 的路由处理 Idempotency-Key：校验 Key、计算摘要、Redis 原子占位，
 * 然后下发并记录下游响应，或直接重放 / 拒绝。位于请求体读取之后、上游超时与转发之前；
 * 认证、Scope、限流与请求体校验失败不会占用 Key。Key 原值与请求体从不写入日志。
 */
@Component
public class IdempotencyGlobalFilter implements GlobalFilter, Ordered {
    private static final Logger LOGGER = LoggerFactory.getLogger(IdempotencyGlobalFilter.class);
    private static final long IN_PROGRESS_RETRY_AFTER_SECONDS = 3;

    private final IdempotencyKeyPolicy keyPolicy;
    private final IdempotencyRequestDigest requestDigest;
    private final IdempotencyRecordStore recordStore;
    private final IdempotencyReplayWriter replayWriter;
    private final IdempotencyProperties properties;
    private final GatewayErrorWriter errorWriter;

    public IdempotencyGlobalFilter(IdempotencyKeyPolicy keyPolicy, IdempotencyRequestDigest requestDigest,
        IdempotencyRecordStore recordStore, IdempotencyReplayWriter replayWriter, IdempotencyProperties properties,
        GatewayErrorWriter errorWriter) {
        this.keyPolicy = Objects.requireNonNull(keyPolicy, "Idempotency-Key 策略不可为空");
        this.requestDigest = Objects.requireNonNull(requestDigest, "请求摘要不可为空");
        this.recordStore = Objects.requireNonNull(recordStore, "请求幂等存储不可为空");
        this.replayWriter = Objects.requireNonNull(replayWriter, "请求幂等重放不可为空");
        this.properties = Objects.requireNonNull(properties, "请求幂等配置不可为空");
        this.errorWriter = Objects.requireNonNull(errorWriter, "Gateway 错误响应不可为空");
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        GatewayRuntimeRoute route = GatewayExchangeAttributes.runtimeRoute(exchange).orElse(null);
        if (route == null || !route.idempotencyRequired()) {
            return chain.filter(exchange);
        }
        KeyResolution key = keyPolicy.resolve(exchange.getRequest().getHeaders());
        if (!key.valid()) {
            exchange.getAttributes().put(GatewayExchangeAttributes.OUTCOME, GatewayOutcome.INVALID_REQUEST);
            return errorWriter.write(exchange, HttpStatus.BAD_REQUEST, key.error(), null);
        }
        Scope scope = new Scope(route.channel().getCode(), callerKey(exchange), requestDigest.keyHash(key.key()));
        return digest(exchange, route)
            .flatMap(digest -> recordStore.claim(scope, digest,
                properties.pendingLease(route.responseTimeout(), route.bodyReceiveTimeout())))
            .onErrorResume(failure -> writeStoreUnavailable(exchange, route, failure).then(Mono.<Claim>empty()))
            .flatMap(claim -> handleClaim(exchange, chain, route, claim));
    }

    @Override
    public int getOrder() {
        return GatewayGlobalFilterOrder.IDEMPOTENCY;
    }

    private Mono<Void> handleClaim(ServerWebExchange exchange, GatewayFilterChain chain, GatewayRuntimeRoute route,
        Claim claim) {
        return switch (claim.outcome()) {
            case CLAIMED -> forwardAndRecord(exchange, chain, route, claim.lease());
            case COMPLETED -> replay(exchange, route, claim.response());
            case IN_PROGRESS -> writeConflict(exchange, GatewayErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS,
                IN_PROGRESS_RETRY_AFTER_SECONDS);
            case KEY_REUSED -> writeConflict(exchange, GatewayErrorCode.IDEMPOTENCY_KEY_REUSED, null);
        };
    }

    private Mono<Void> forwardAndRecord(ServerWebExchange exchange, GatewayFilterChain chain,
        GatewayRuntimeRoute route, Lease lease) {
        LeaseSettlement settlement = new LeaseSettlement(route.routeCode(), lease);
        IdempotencyResponseCapture capture =
            new IdempotencyResponseCapture(exchange, properties.maxResponseBytes(), settlement);
        return chain.filter(exchange.mutate().response(capture).build())
            .onErrorResume(failure -> settlement.release(ReleaseReason.REQUEST_FAILED).then(Mono.error(failure)))
            .then(Mono.defer(() -> settlement.release(ReleaseReason.NO_DOWNSTREAM_RESPONSE)))
            .doOnCancel(() -> settlement.release(ReleaseReason.CLIENT_CANCELLED).subscribe());
    }

    private Mono<Void> replay(ServerWebExchange exchange, GatewayRuntimeRoute route, StoredResponse response) {
        exchange.getAttributes().put(GatewayExchangeAttributes.OUTCOME, GatewayOutcome.IDEMPOTENCY_REPLAYED);
        return replayWriter.write(exchange, response, route.routeCode());
    }

    private Mono<Void> writeConflict(ServerWebExchange exchange, GatewayErrorCode errorCode, Long retryAfterSeconds) {
        exchange.getAttributes().put(GatewayExchangeAttributes.OUTCOME, GatewayOutcome.IDEMPOTENCY_CONFLICT);
        return errorWriter.write(exchange, HttpStatus.CONFLICT, errorCode, retryAfterSeconds);
    }

    private Mono<Void> writeStoreUnavailable(ServerWebExchange exchange, GatewayRuntimeRoute route,
        Throwable failure) {
        LOGGER.warn("请求幂等存储不可用，拒绝下发: routeCode={}, failure={}", route.routeCode(),
            failure.getClass().getSimpleName());
        exchange.getAttributes().put(GatewayExchangeAttributes.OUTCOME, GatewayOutcome.DEPENDENCY_UNAVAILABLE);
        return errorWriter.write(exchange, HttpStatus.SERVICE_UNAVAILABLE,
            GatewayErrorCode.GATEWAY_DEPENDENCY_UNAVAILABLE, null);
    }

    /** 请求体已由 +100 读入可重放缓冲；此处重放一份计算摘要，不改变后续转发内容。 */
    private Mono<String> digest(ServerWebExchange exchange, GatewayRuntimeRoute route) {
        ServerHttpRequest request = exchange.getRequest();
        return DataBufferUtils.join(request.getBody()).map(IdempotencyGlobalFilter::copyAndRelease)
            .defaultIfEmpty(new byte[0])
            .map(body -> requestDigest.digest(request.getMethod().name(), route.routeCode(),
                request.getURI().getRawPath(), request.getURI().getRawQuery(), body));
    }

    /** 作用域按调用方而非凭证：Client API 使用 Client ID，凭证轮换不影响重放。 */
    private String callerKey(ServerWebExchange exchange) {
        GatewayAuthenticatedIdentity identity = GatewayExchangeAttributes.authenticatedIdentity(exchange)
            .orElseThrow(() -> new IllegalStateException("请求幂等路由缺少已验证调用方身份"));
        return identity instanceof ClientApiIdentity clientApiIdentity ? clientApiIdentity.clientId()
            : identity.principalKey();
    }

    private static byte[] copyAndRelease(DataBuffer buffer) {
        try {
            byte[] bytes = new byte[buffer.readableByteCount()];
            buffer.read(bytes);
            return bytes;
        } finally {
            DataBufferUtils.release(buffer);
        }
    }

    /** 记录与释放互斥且只执行一次；存储失败只记 WARN，不改变已经产生的响应。 */
    private final class LeaseSettlement implements IdempotencyResponseCapture.Settlement {
        private final String routeCode;
        private final Lease lease;
        private final AtomicBoolean settled = new AtomicBoolean();

        private LeaseSettlement(String routeCode, Lease lease) {
            this.routeCode = routeCode;
            this.lease = lease;
        }

        @Override
        public Mono<Void> record(StoredResponse response) {
            if (!settled.compareAndSet(false, true)) {
                return Mono.empty();
            }
            return recordStore.complete(lease, response, properties.retention()).doOnNext(completed -> {
                if (!completed) {
                    LOGGER.warn("请求幂等占位已失效，未记录响应: routeCode={}, status={}", routeCode, response.status());
                }
            }).onErrorResume(failure -> {
                LOGGER.warn("请求幂等响应记录失败，响应照常返回: routeCode={}, status={}, failure={}", routeCode,
                    response.status(), failure.getClass().getSimpleName());
                return Mono.empty();
            }).then();
        }

        @Override
        public Mono<Void> release(ReleaseReason reason) {
            if (!settled.compareAndSet(false, true)) {
                return Mono.empty();
            }
            if (reason == ReleaseReason.RESPONSE_TOO_LARGE) {
                LOGGER.warn("请求幂等响应体超过记录上限，未记录并释放占位: routeCode={}", routeCode);
            }
            return recordStore.release(lease).onErrorResume(failure -> {
                LOGGER.warn("请求幂等占位释放失败，等待租约过期: routeCode={}, reason={}, failure={}", routeCode, reason,
                    failure.getClass().getSimpleName());
                return Mono.empty();
            });
        }
    }
}
