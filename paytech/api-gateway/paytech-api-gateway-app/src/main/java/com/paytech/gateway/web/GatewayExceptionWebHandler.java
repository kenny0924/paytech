package com.paytech.gateway.web;

import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;

import com.paytech.foundation.common.enums.CodeMessageEnum;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.gateway.observability.GatewayOutcome;
import com.paytech.gateway.web.GatewayUpstreamFailureClassifier.Failure;

import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import reactor.netty.channel.AbortedException;

/** 将 Gateway 自己可识别的未提交异常转换为稳定错误响应。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayExceptionWebHandler implements WebExceptionHandler {
    private static final String UNMATCHED_ROUTE_CODE = "UNMATCHED";
    private static final Map<Failure, GatewayOutcome> CLASSIFIED_OUTCOMES =
        Map.of(Failure.INVALID_RESPONSE, GatewayOutcome.UPSTREAM_INVALID_RESPONSE, Failure.DEPENDENCY_UNAVAILABLE,
            GatewayOutcome.DEPENDENCY_UNAVAILABLE, Failure.UPSTREAM_TIMEOUT, GatewayOutcome.UPSTREAM_TIMEOUT);

    private final GatewayErrorWriter errorWriter;
    private final GatewayUpstreamFailureClassifier failureClassifier;

    public GatewayExceptionWebHandler(GatewayErrorWriter errorWriter,
        GatewayUpstreamFailureClassifier failureClassifier) {
        this.errorWriter = errorWriter;
        this.failureClassifier = failureClassifier;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable throwable) {
        if (exchange.getResponse().isCommitted()) {
            return handleCommitted(exchange, throwable);
        }
        if (isRouteNotFound(throwable)) {
            return writeRouteNotFound(exchange);
        }
        Optional<Failure> classifiedFailure = failureClassifier.classify(throwable);
        if (classifiedFailure.isPresent()) {
            return writeClassified(exchange, classifiedFailure.orElseThrow());
        }
        if (isClientTransportAbort(throwable)) {
            return Mono.error(throwable);
        }
        if (throwable instanceof ResponseStatusException responseStatusException) {
            return writeResponseStatusException(exchange, responseStatusException);
        }
        if (throwable instanceof RuntimeException) {
            return errorWriter.write(exchange, HttpStatus.INTERNAL_SERVER_ERROR, CommonErrorCode.INTERNAL_ERROR, null);
        }
        return Mono.error(throwable);
    }

    private Mono<Void> writeRouteNotFound(ServerWebExchange exchange) {
        exchange.getAttributes().put(GatewayExchangeAttributes.ROUTE_CODE, UNMATCHED_ROUTE_CODE);
        exchange.getAttributes().put(GatewayExchangeAttributes.OUTCOME, GatewayOutcome.ROUTE_NOT_FOUND);
        return errorWriter.write(exchange, HttpStatus.NOT_FOUND, GatewayErrorCode.GATEWAY_ROUTE_NOT_FOUND, null);
    }

    private Mono<Void> writeDependencyError(ServerWebExchange exchange, HttpStatus status, GatewayErrorCode errorCode,
        GatewayOutcome outcome) {
        exchange.getAttributes().put(GatewayExchangeAttributes.OUTCOME, outcome);
        return errorWriter.write(exchange, status, errorCode, null);
    }

    private boolean isRouteNotFound(Throwable throwable) {
        return throwable instanceof ResponseStatusException exception
            && exception.getStatusCode() == HttpStatus.NOT_FOUND;
    }

    private Mono<Void> writeClassified(ServerWebExchange exchange, Failure failure) {
        if (failure == Failure.INVALID_RESPONSE) {
            exchange.getAttributes().put(GatewayExchangeAttributes.OUTCOME, GatewayOutcome.UPSTREAM_INVALID_RESPONSE);
            return errorWriter.replace(exchange, HttpStatus.BAD_GATEWAY, GatewayErrorCode.UPSTREAM_PROTOCOL_ERROR);
        }
        if (failure == Failure.DEPENDENCY_UNAVAILABLE) {
            return writeDependencyError(exchange, HttpStatus.SERVICE_UNAVAILABLE,
                GatewayErrorCode.GATEWAY_DEPENDENCY_UNAVAILABLE, GatewayOutcome.DEPENDENCY_UNAVAILABLE);
        }
        return writeDependencyError(exchange, HttpStatus.GATEWAY_TIMEOUT, GatewayErrorCode.GATEWAY_UPSTREAM_TIMEOUT,
            GatewayOutcome.UPSTREAM_TIMEOUT);
    }

    private Mono<Void> handleCommitted(ServerWebExchange exchange, Throwable throwable) {
        return failureClassifier.classify(throwable).map(failure -> {
            exchange.getAttributes().put(GatewayExchangeAttributes.OUTCOME, outcome(failure));
            return Mono.<Void>error(sanitized(failure));
        }).orElseGet(() -> Mono.error(throwable));
    }

    private GatewayOutcome outcome(Failure failure) {
        return CLASSIFIED_OUTCOMES.get(failure);
    }

    private RuntimeException sanitized(Failure failure) {
        return failure == Failure.UPSTREAM_TIMEOUT ? new GatewayUpstreamTimeoutException()
            : new GatewayUpstreamProtocolException();
    }

    private Mono<Void> writeResponseStatusException(ServerWebExchange exchange, ResponseStatusException exception) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        if (status == null || !status.isError()) {
            return Mono.error(exception);
        }
        CodeMessageEnum errorCode = responseStatusErrorCode(status);
        responseStatusOutcome(status).ifPresent(
            outcome -> exchange.getAttributes().put(GatewayExchangeAttributes.OUTCOME, outcome));
        return errorWriter.write(exchange, status, errorCode, null);
    }

    private CodeMessageEnum responseStatusErrorCode(HttpStatus status) {
        return switch (status) {
            case UNAUTHORIZED -> GatewayErrorCode.GATEWAY_UNAUTHORIZED;
            case FORBIDDEN -> GatewayErrorCode.GATEWAY_FORBIDDEN;
            case NOT_FOUND -> GatewayErrorCode.GATEWAY_ROUTE_NOT_FOUND;
            case REQUEST_TIMEOUT -> GatewayErrorCode.GATEWAY_REQUEST_TIMEOUT;
            case CONTENT_TOO_LARGE -> GatewayErrorCode.GATEWAY_PAYLOAD_TOO_LARGE;
            case REQUEST_HEADER_FIELDS_TOO_LARGE -> GatewayErrorCode.GATEWAY_REQUEST_HEADER_FIELDS_TOO_LARGE;
            case BAD_GATEWAY -> GatewayErrorCode.UPSTREAM_PROTOCOL_ERROR;
            case SERVICE_UNAVAILABLE -> GatewayErrorCode.GATEWAY_DEPENDENCY_UNAVAILABLE;
            case GATEWAY_TIMEOUT -> GatewayErrorCode.GATEWAY_UPSTREAM_TIMEOUT;
            default -> status.is4xxClientError() ? GatewayErrorCode.GATEWAY_INVALID_REQUEST
                : CommonErrorCode.INTERNAL_ERROR;
        };
    }

    private Optional<GatewayOutcome> responseStatusOutcome(HttpStatus status) {
        return switch (status) {
            case UNAUTHORIZED -> Optional.of(GatewayOutcome.AUTHENTICATION_FAILED);
            case FORBIDDEN -> Optional.of(GatewayOutcome.AUTHORIZATION_DENIED);
            case NOT_FOUND -> Optional.of(GatewayOutcome.ROUTE_NOT_FOUND);
            case REQUEST_TIMEOUT -> Optional.of(GatewayOutcome.REQUEST_TIMEOUT);
            case CONTENT_TOO_LARGE -> Optional.of(GatewayOutcome.PAYLOAD_TOO_LARGE);
            case REQUEST_HEADER_FIELDS_TOO_LARGE -> Optional.of(GatewayOutcome.REQUEST_HEADER_FIELDS_TOO_LARGE);
            case BAD_GATEWAY -> Optional.of(GatewayOutcome.UPSTREAM_INVALID_RESPONSE);
            case SERVICE_UNAVAILABLE -> Optional.of(GatewayOutcome.DEPENDENCY_UNAVAILABLE);
            case GATEWAY_TIMEOUT -> Optional.of(GatewayOutcome.UPSTREAM_TIMEOUT);
            default -> status.is4xxClientError() ? Optional.of(GatewayOutcome.INVALID_REQUEST) : Optional.empty();
        };
    }

    private boolean isClientTransportAbort(Throwable throwable) {
        IdentityHashMap<Throwable, Boolean> visited = new IdentityHashMap<>();
        Throwable current = throwable;
        while (current != null && visited.size() < 32 && visited.put(current, Boolean.TRUE) == null) {
            if (current instanceof CancellationException || Exceptions.isCancel(current)
                || current instanceof AbortedException) {
                return true;
            }
            if (current instanceof IOException && isDisconnectMessage(current.getMessage())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isDisconnectMessage(String message) {
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("broken pipe") || normalized.contains("connection reset by peer");
    }
}
