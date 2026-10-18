package com.paytech.gateway.web;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.paytech.foundation.common.enums.CodeMessageEnum;
import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.foundation.httpproblem.api.ApiProblemResponse;

import reactor.core.publisher.Mono;

/** 写入不包含请求材料的 Gateway 本地错误响应。 */
@Component
public class GatewayErrorWriter {
    private static final MediaType PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON;
    private static final ResolvableType RESPONSE_TYPE = ResolvableType.forClass(ApiProblemResponse.class);

    private final HttpMessageWriter<Object> jsonWriter;
    private final Clock clock;

    @Autowired
    public GatewayErrorWriter(ServerCodecConfigurer codecConfigurer) {
        this(codecConfigurer, Clock.systemUTC());
    }

    public GatewayErrorWriter(ServerCodecConfigurer codecConfigurer, Clock clock) {
        Objects.requireNonNull(codecConfigurer, "codecConfigurer must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.jsonWriter = codecConfigurer.getWriters().stream()
            .filter(writer -> writer.canWrite(RESPONSE_TYPE, PROBLEM_JSON)).findFirst().map(this::castWriter)
            .orElseThrow(() -> new IllegalStateException("Gateway Problem JSON codec is unavailable"));
    }

    public Mono<Void> write(ServerWebExchange exchange, HttpStatus status, CodeMessageEnum errorCode,
        Long retryAfterSeconds) {
        Objects.requireNonNull(exchange, "exchange must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(errorCode, "errorCode must not be null");
        if (exchange.getResponse().isCommitted()) {
            return Mono.empty();
        }
        if (retryAfterSeconds != null && retryAfterSeconds < 0) {
            return Mono.error(new IllegalArgumentException("retryAfterSeconds must not be negative"));
        }
        String requestId = requestId(exchange);
        prepareResponse(exchange, status, requestId, retryAfterSeconds);
        if (exchange.getRequest().getMethod() == HttpMethod.HEAD) {
            return exchange.getResponse().setComplete();
        }
        Instant timestamp = clock.instant();
        ApiProblemResponse response =
            new ApiProblemResponse(ApiProblemProfile.TYPE, HttpStatus.valueOf(status.value()).getReasonPhrase(),
                errorCode.getMessage(), externalCode(exchange, errorCode), requestId, ApiProblemProfile.formatTimestamp(timestamp),
                null);
        return jsonWriter.write(Mono.just(response), RESPONSE_TYPE, PROBLEM_JSON, exchange.getResponse(), Map.of());
    }

    public Mono<Void> replace(ServerWebExchange exchange, HttpStatus status, CodeMessageEnum errorCode) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.empty();
        }
        exchange.getResponse().getHeaders().clear();
        exchange.getResponse().setStatusCode(null);
        return write(exchange, status, errorCode, null);
    }

    private String externalCode(ServerWebExchange exchange, CodeMessageEnum errorCode) {
        return errorCode instanceof GatewayErrorCode gatewayCode && GatewayExchangeAttributes.entryChannel(exchange)
            .filter(channel -> channel == com.paytech.platform.support.api.enums.GatewayRouteChannel.CLIENT_API).isPresent()
                ? gatewayCode.clientApiCode() : errorCode.getCode();
    }

    private String requestId(ServerWebExchange exchange) {
        Object value = exchange.getAttributes().get(GatewayExchangeAttributes.REQUEST_ID);
        if (value instanceof String requestId && ApiProblemProfile.isValidRequestId(requestId)) {
            return requestId;
        }
        String generatedRequestId = UUID.randomUUID().toString();
        if (!ApiProblemProfile.isValidRequestId(generatedRequestId)) {
            throw new IllegalStateException("Generated request ID does not match the Problem Profile");
        }
        exchange.getAttributes().put(GatewayExchangeAttributes.REQUEST_ID, generatedRequestId);
        return generatedRequestId;
    }

    private void prepareResponse(ServerWebExchange exchange, HttpStatus status, String requestId,
        Long retryAfterSeconds) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(PROBLEM_JSON);
        exchange.getResponse().getHeaders().set(ApiProblemProfile.REQUEST_ID_HEADER, requestId);
        exchange.getResponse().getHeaders().setDate(System.currentTimeMillis());
        exchange.getResponse().getHeaders().remove("X-Timestamp");
        if (retryAfterSeconds != null) {
            exchange.getResponse().getHeaders().set(HttpHeaders.RETRY_AFTER, retryAfterSeconds.toString());
        }
    }

    @SuppressWarnings("unchecked")
    private HttpMessageWriter<Object> castWriter(HttpMessageWriter<?> writer) {
        return (HttpMessageWriter<Object>)writer;
    }
}
