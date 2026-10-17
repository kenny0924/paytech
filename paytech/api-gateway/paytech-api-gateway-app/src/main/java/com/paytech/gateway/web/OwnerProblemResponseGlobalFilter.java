package com.paytech.gateway.web;

import java.util.Objects;

import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebExchangeDecorator;

import com.paytech.gateway.routing.runtime.GatewayRuntimeRoute;

import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClientResponse;

/** Buffers and validates only routed Owner error responses before terminal headers are rebuilt. */
@Component
public final class OwnerProblemResponseGlobalFilter implements GlobalFilter, Ordered {
    /** Absolute Gateway-owned Owner error response ceiling; unrelated to dynamic request-body limits. */
    public static final int MAX_OWNER_ERROR_BODY_BYTES = 65_536;
    private static final String LEGACY_TARGET = "operations-console-bff";

    private final OwnerProblemValidator validator;
    private final TrustedTerminalResponseHeaders trustedHeaders;

    public OwnerProblemResponseGlobalFilter(OwnerProblemValidator validator,
        TrustedTerminalResponseHeaders trustedHeaders) {
        this.validator = Objects.requireNonNull(validator, "Owner Problem 校验器不可为空");
        this.trustedHeaders = Objects.requireNonNull(trustedHeaders, "终端可信响应 Header 不可为空");
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        GatewayRuntimeRoute route = GatewayExchangeAttributes.runtimeRoute(exchange).orElse(null);
        ServerHttpResponseDecorator decorated = new ServerHttpResponseDecorator(exchange.getResponse()) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                OwnerResponse ownerResponse;
                try {
                    ownerResponse = ownerResponse(exchange, route);
                } catch (RuntimeException failure) {
                    return drainAndFail(body, failure);
                }
                if (ownerResponse == null) {
                    return super.writeWith(body);
                }
                if (exchange.getRequest().getMethod() == HttpMethod.HEAD) {
                    return validateHead(exchange, ownerResponse, body, () -> super.writeWith(Mono.empty()));
                }
                return validateBody(exchange, route, ownerResponse, body, this::writeReplay);
            }

            @Override
            public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
                OwnerResponse ownerResponse;
                try {
                    ownerResponse = ownerResponse(exchange, route);
                } catch (RuntimeException failure) {
                    return drainAndFail(Flux.from(body).concatMap(Flux::from), failure);
                }
                if (ownerResponse == null) {
                    return super.writeAndFlushWith(body);
                }
                Flux<DataBuffer> flattened = Flux.from(body).concatMap(Flux::from);
                if (exchange.getRequest().getMethod() == HttpMethod.HEAD) {
                    return validateHead(exchange, ownerResponse, flattened,
                        () -> super.writeAndFlushWith(Mono.empty()));
                }
                return validateBody(exchange, route, ownerResponse, flattened, this::writeReplay);
            }

            private Mono<Void> writeReplay(byte[] bytes) {
                Mono<DataBuffer> replay = Mono.fromSupplier(() -> bufferFactory().wrap(bytes))
                    .doOnDiscard(DataBuffer.class, DataBufferUtils::release);
                return super.writeWith(replay);
            }
        };
        ServerWebExchange decoratedExchange = exchange.mutate().response(decorated).build();
        return chain.filter(frameworkSafeExchange(decoratedExchange, route));
    }

    @Override
    public int getOrder() {
        return GatewayGlobalFilterOrder.OWNER_PROBLEM_RESPONSE;
    }

    private ServerWebExchange frameworkSafeExchange(ServerWebExchange exchange, GatewayRuntimeRoute route) {
        if (route == null) {
            return exchange;
        }
        return new ServerWebExchangeDecorator(exchange) {
            @Override
            public <T> T getAttribute(String name) {
                if (ServerWebExchangeUtils.CLIENT_RESPONSE_ATTR.equals(name)) {
                    Object candidate = getAttributes().get(name);
                    if (candidate != null && !(candidate instanceof HttpClientResponse)) {
                        return null;
                    }
                }
                return super.getAttribute(name);
            }
        };
    }

    private OwnerResponse ownerResponse(ServerWebExchange exchange, GatewayRuntimeRoute route) {
        if (route == null) {
            return null;
        }
        Object candidate = exchange.getAttribute(ServerWebExchangeUtils.CLIENT_RESPONSE_ATTR);
        if (!(candidate instanceof HttpClientResponse nativeResponse)) {
            HttpStatusCode mergedStatus = exchange.getResponse().getStatusCode();
            if (mergedStatus != null && mergedStatus.isError()) {
                throw new GatewayUpstreamProtocolException();
            }
            return null;
        }
        try {
            HttpStatusCode nativeStatus = HttpStatusCode.valueOf(nativeResponse.status().code());
            if (!nativeStatus.isError()) {
                return null;
            }
            HttpHeaders nativeHeaders = new HttpHeaders();
            nativeResponse.responseHeaders()
                .forEach(entry -> nativeHeaders.add(entry.getKey(), entry.getValue()));
            return new OwnerResponse(nativeStatus, HttpHeaders.readOnlyHttpHeaders(nativeHeaders));
        } catch (RuntimeException failure) {
            throw new GatewayUpstreamProtocolException();
        }
    }

    private Mono<Void> validateBody(ServerWebExchange exchange, GatewayRuntimeRoute route,
        OwnerResponse ownerResponse, Publisher<? extends DataBuffer> body,
        java.util.function.Function<byte[], Mono<Void>> writer) {
        Mono<byte[]> validated = DataBufferUtils.join(Flux.from(body), MAX_OWNER_ERROR_BODY_BYTES)
            .switchIfEmpty(Mono.error(new GatewayUpstreamProtocolException())).map(this::copyAndRelease)
            .map(bytes -> {
                validator.validate(ownerResponse.status(), ownerResponse.headers(), bytes, trustedRequestId(exchange),
                    LEGACY_TARGET.equals(route.targetServiceCode()));
                return bytes;
            }).onErrorMap(this::shouldSanitize, failure -> new GatewayUpstreamProtocolException());
        return validated.flatMap(writer);
    }

    private Mono<Void> validateHead(ServerWebExchange exchange, OwnerResponse ownerResponse,
        Publisher<? extends DataBuffer> body, java.util.function.Supplier<Mono<Void>> writer) {
        GatewayUpstreamProtocolException headerFailure = null;
        try {
            validator.validateHead(ownerResponse.status(), ownerResponse.headers(), trustedRequestId(exchange));
        } catch (RuntimeException failure) {
            headerFailure = failure instanceof GatewayUpstreamProtocolException protocolFailure ? protocolFailure
                : new GatewayUpstreamProtocolException();
        }
        GatewayUpstreamProtocolException validationFailure = headerFailure;
        Mono<Boolean> drained = Flux.from(body).map(buffer -> {
            try {
                return buffer.readableByteCount() > 0;
            } finally {
                DataBufferUtils.release(buffer);
            }
        }).doOnDiscard(DataBuffer.class, DataBufferUtils::release).reduce(false, (left, right) -> left || right)
            .onErrorMap(this::shouldSanitize, failure -> new GatewayUpstreamProtocolException());
        return drained.flatMap(hasBody -> {
            if (validationFailure != null || hasBody) {
                return Mono.error(validationFailure == null ? new GatewayUpstreamProtocolException()
                    : validationFailure);
            }
            return Mono.defer(writer);
        });
    }

    private Mono<Void> drainAndFail(Publisher<? extends DataBuffer> body, Throwable failure) {
        GatewayUpstreamProtocolException protocolFailure = failure instanceof GatewayUpstreamProtocolException existing
            ? existing : new GatewayUpstreamProtocolException();
        return Flux.from(body).map(buffer -> {
            DataBufferUtils.release(buffer);
            return false;
        }).doOnDiscard(DataBuffer.class, DataBufferUtils::release).then(Mono.<Void>error(protocolFailure))
            .onErrorMap(this::shouldSanitize, ignored -> new GatewayUpstreamProtocolException());
    }

    private byte[] copyAndRelease(DataBuffer buffer) {
        try {
            byte[] bytes = new byte[buffer.readableByteCount()];
            buffer.read(bytes);
            return bytes;
        } finally {
            DataBufferUtils.release(buffer);
        }
    }

    private String trustedRequestId(ServerWebExchange exchange) {
        try {
            return trustedHeaders.trustedRequestId(exchange)
                .orElseThrow(GatewayUpstreamProtocolException::new);
        } catch (GatewayUpstreamProtocolException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new GatewayUpstreamProtocolException();
        }
    }

    private boolean shouldSanitize(Throwable failure) {
        return !(failure instanceof GatewayUpstreamProtocolException) && !Exceptions.isCancel(failure);
    }

    private record OwnerResponse(HttpStatusCode status, HttpHeaders headers) {
    }
}
