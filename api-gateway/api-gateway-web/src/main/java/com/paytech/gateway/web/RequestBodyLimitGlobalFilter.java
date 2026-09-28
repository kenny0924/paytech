package com.paytech.gateway.web;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.paytech.gateway.observability.GatewayOutcome;
import com.paytech.gateway.routing.runtime.GatewayRuntimeRoute;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

/** 以 Route 目录为准限制并持有请求生命周期内可重放的 Operations 请求体。 */
@Component
public class RequestBodyLimitGlobalFilter implements GlobalFilter, Ordered {
    private final GatewayErrorWriter errorWriter;

    public RequestBodyLimitGlobalFilter(GatewayErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        GatewayRuntimeRoute route = GatewayExchangeAttributes.runtimeRoute(exchange).orElse(null);
        if (route == null) {
            return chain.filter(exchange);
        }
        long contentLength = exchange.getRequest().getHeaders().getContentLength();
        if (contentLength > route.bodyLimitBytes()) {
            return writePayloadTooLarge(exchange);
        }
        Mono<DataBuffer> aggregateBody = aggregateRequestBody(exchange, Math.toIntExact(route.bodyLimitBytes()))
            .timeout(route.bodyReceiveTimeout(), Mono.error(new RequestBodyReceiveTimeoutException()));
        return aggregateBody
            .flatMap(
                buffer -> forwardWithReplayableBody(exchange, chain, buffer, Math.toIntExact(route.bodyLimitBytes())))
            .onErrorResume(RequestPayloadTooLargeException.class, exception -> writePayloadTooLarge(exchange))
            .onErrorResume(RequestBodyReceiveTimeoutException.class, exception -> writeRequestTimeout(exchange));
    }

    @Override
    public int getOrder() {
        return GatewayGlobalFilterOrder.REQUEST_BODY_LIMIT;
    }

    private Mono<Void> forwardWithReplayableBody(ServerWebExchange exchange, GatewayFilterChain chain,
        DataBuffer buffer, int limit) {
        if (buffer.readableByteCount() > limit) {
            DataBufferUtils.release(buffer);
            return writePayloadTooLarge(exchange);
        }
        byte[] body = copyAndRelease(buffer);
        ReplayableRequestBodyOwner owner;
        try {
            owner = new ReplayableRequestBodyOwner(body);
        } catch (Throwable throwable) {
            zeroBestEffort(body);
            throw throwable;
        }
        return Mono.usingWhen(Mono.just(owner), ignored -> forwardDownstream(exchange, chain, owner),
            ignored -> clearOwner(owner, SignalType.ON_COMPLETE),
            (ignored, throwable) -> clearOwner(owner, SignalType.ON_ERROR),
            ignored -> clearOwner(owner, SignalType.CANCEL));
    }

    private Mono<Void> forwardDownstream(ServerWebExchange exchange, GatewayFilterChain chain,
        ReplayableRequestBodyOwner owner) {
        return Mono.defer(() -> {
            exchange.getAttributes().put(GatewayExchangeAttributes.ACTUAL_REQUEST_BODY_LENGTH, (long)owner.length());
            exchange.getAttributes().put(GatewayExchangeAttributes.REPLAYABLE_REQUEST_BODY_OWNER, owner);
            return chain.filter(withReplayableRequest(exchange, owner));
        });
    }

    private ServerWebExchange withReplayableRequest(ServerWebExchange exchange, ReplayableRequestBodyOwner owner) {
        ServerHttpRequest replayableRequest = new ServerHttpRequestDecorator(exchange.getRequest()) {
            @Override
            public HttpHeaders getHeaders() {
                HttpHeaders headers = new HttpHeaders();
                headers.putAll(super.getHeaders());
                headers.setContentLength(owner.length());
                headers.remove(HttpHeaders.TRANSFER_ENCODING);
                return headers;
            }

            @Override
            public Flux<DataBuffer> getBody() {
                return owner.replay(exchange.getResponse().bufferFactory());
            }
        };
        return exchange.mutate().request(replayableRequest).build();
    }

    private byte[] copyAndRelease(DataBuffer buffer) {
        byte[] body = null;
        try {
            body = new byte[buffer.readableByteCount()];
            buffer.read(body);
            return body;
        } catch (Throwable throwable) {
            zeroBestEffort(body);
            throw throwable;
        } finally {
            DataBufferUtils.release(buffer);
        }
    }

    private void zeroBestEffort(byte[] body) {
        try {
            java.util.Arrays.fill(body, (byte)0);
        } catch (Throwable ignored) {
            // 清理不得掩盖原始失败。
        }
    }

    private Mono<DataBuffer> aggregateRequestBody(ServerWebExchange exchange, int limit) {
        int aggregateLimit = Math.addExact(limit, 1);
        return DataBufferUtils.join(exchange.getRequest().getBody(), aggregateLimit)
            .defaultIfEmpty(exchange.getResponse().bufferFactory().wrap(new byte[0]))
            .onErrorMap(DataBufferLimitException.class, exception -> new RequestPayloadTooLargeException());
    }

    private Mono<Void> writePayloadTooLarge(ServerWebExchange exchange) {
        exchange.getAttributes().put(GatewayExchangeAttributes.OUTCOME, GatewayOutcome.PAYLOAD_TOO_LARGE);
        return errorWriter.write(exchange, HttpStatus.PAYLOAD_TOO_LARGE, GatewayErrorCode.GATEWAY_PAYLOAD_TOO_LARGE,
            null);
    }

    private Mono<Void> writeRequestTimeout(ServerWebExchange exchange) {
        exchange.getAttributes().put(GatewayExchangeAttributes.OUTCOME, GatewayOutcome.REQUEST_TIMEOUT);
        return errorWriter.write(exchange, HttpStatus.REQUEST_TIMEOUT, GatewayErrorCode.GATEWAY_REQUEST_TIMEOUT, null);
    }

    private Mono<Void> clearOwner(ReplayableRequestBodyOwner owner, SignalType signalType) {
        owner.clear(signalType);
        return Mono.empty();
    }

    private static final class RequestPayloadTooLargeException extends RuntimeException {}

    private static final class RequestBodyReceiveTimeoutException extends RuntimeException {}
}
