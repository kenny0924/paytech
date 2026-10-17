package com.paytech.gateway.observability;

import java.net.InetAddress;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.boot.webflux.autoconfigure.WebHttpHandlerBuilderCustomizer;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;

import com.paytech.gateway.routing.runtime.GatewayRuntimeRoute;
import com.paytech.gateway.security.VersionedHmacDigester;
import com.paytech.gateway.security.VersionedHmacDigester.VersionedDigest;
import com.paytech.gateway.web.GatewayExchangeAttributes;
import com.paytech.gateway.web.TrustedTerminalResponseHeaders;
import com.paytech.platform.support.api.enums.GatewayRouteChannel;

import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

/** 在最外层为每个 Gateway 请求写入一次有界、脱敏的终态访问事件。 */
@Component
public final class GatewayAccessLogWebFilter implements WebFilter, Ordered, WebHttpHandlerBuilderCustomizer {
    private static final Logger ACCESS_LOG = LoggerFactory.getLogger("gateway.access");
    private static final String UNMATCHED_ROUTE = "UNMATCHED";
    private static final Pattern TRACE_ID_PATTERN = Pattern.compile("(?!0{32})[0-9a-f]{32}");
    private static final Pattern ACCESS_DIGEST_PATTERN =
        Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}:[A-Za-z0-9_-]{43}");

    private final VersionedHmacDigester digester;
    private final GatewayMetrics gatewayMetrics;
    private final TrustedTerminalResponseHeaders trustedResponseHeaders;

    public GatewayAccessLogWebFilter(VersionedHmacDigester digester, GatewayMetrics gatewayMetrics,
        TrustedTerminalResponseHeaders trustedResponseHeaders) {
        this.digester = Objects.requireNonNull(digester, "访问日志摘要器不可为空");
        this.gatewayMetrics = Objects.requireNonNull(gatewayMetrics, "Gateway Metrics 不可为空");
        this.trustedResponseHeaders = Objects.requireNonNull(trustedResponseHeaders, "可信终端响应 Header 保护器不可为空");
    }

    private static String encoded(VersionedDigest digest) {
        return digest.version() + ":" + digest.digest();
    }

    private static long elapsedNanos(long startedAtNanos) {
        return Math.max(0, System.nanoTime() - startedAtNanos);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        TerminalContext context = new TerminalContext(exchange, System.nanoTime());
        TerminalObservingResponse terminalResponse = terminalResponse(exchange.getResponse());
        if (terminalResponse != null) {
            terminalResponse.attach(context);
            return chain.filter(exchange);
        }
        return chain.filter(exchange).doFinally(signal -> writeDirectFallback(context, signal));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void customize(WebHttpHandlerBuilder builder) {
        builder.httpHandlerDecorator(this::observeTerminalResponse);
    }

    private HttpHandler observeTerminalResponse(HttpHandler delegate) {
        return (request, response) -> {
            TerminalObservingResponse terminalResponse = new TerminalObservingResponse(response);
            return Mono.defer(() -> delegate.handle(request, terminalResponse))
                .doFinally(signal -> writeOuterTermination(terminalResponse.detach(), signal));
        };
    }

    private void writeOuterTermination(TerminalContext context, SignalType signal) {
        if (context == null) {
            return;
        }
        if (signal == SignalType.CANCEL) {
            writeTerminalEvent(context, TerminalStatus.none(), GatewayOutcome.CLIENT_CANCELLED);
            return;
        }
        if (connectionAborted(context)) {
            writeTerminalEvent(context, TerminalStatus.none(), resolvedOutcome(context.exchange()));
            return;
        }
        if (signal == SignalType.ON_COMPLETE) {
            writeTerminalEvent(context, TerminalStatus.http(responseStatus(context.exchange())),
                resolvedOutcome(context.exchange()));
            return;
        }
        if (signal == SignalType.ON_ERROR) {
            writeTerminalEvent(context, TerminalStatus.none(), resolvedOutcome(context.exchange()));
        }
    }

    private void writeDirectFallback(TerminalContext context, SignalType signal) {
        if (signal == SignalType.CANCEL) {
            writeTerminalEvent(context, TerminalStatus.none(), GatewayOutcome.CLIENT_CANCELLED);
            return;
        }
        if (signal == SignalType.ON_COMPLETE) {
            writeTerminalEvent(context, TerminalStatus.http(responseStatus(context.exchange())),
                resolvedOutcome(context.exchange()));
        }
    }

    private boolean connectionAborted(TerminalContext context) {
        return ServerRequestObservationContext.findCurrent(context.exchange().getAttributes())
            .map(ServerRequestObservationContext::isConnectionAborted).orElse(false);
    }

    private TerminalObservingResponse terminalResponse(ServerHttpResponse response) {
        ServerHttpResponse candidate = response;
        while (candidate instanceof ServerHttpResponseDecorator decorator) {
            if (candidate instanceof TerminalObservingResponse terminalResponse) {
                return terminalResponse;
            }
            candidate = decorator.getDelegate();
        }
        return null;
    }

    private void writeTerminalEvent(TerminalContext context, TerminalStatus status, GatewayOutcome outcome) {
        if (!context.claimTerminalEvent()) {
            return;
        }
        ServerWebExchange exchange = context.exchange();
        GatewayRuntimeRoute route = GatewayExchangeAttributes.runtimeRoute(exchange).orElse(null);
        String routeCode = routeCode(exchange);
        long durationNanos = elapsedNanos(context.startedAtNanos());
        gatewayMetrics.recordRequest(routeCode, route == null ? null : route.channel(),
            route == null ? null : route.securityProfileCode(), outcome, status.family(), durationNanos);
        LoggingEventBuilder event = ACCESS_LOG.atInfo().addKeyValue("route", routeCode)
            .addKeyValue("method", exchange.getRequest().getMethod().name()).addKeyValue("status", status.value())
            .addKeyValue("status_family", status.family())
            .addKeyValue("duration_ms", TimeUnit.NANOSECONDS.toMillis(durationNanos))
            .addKeyValue("outcome", outcome.name()).addKeyValue("request_id", requestId(exchange))
            .addKeyValue("ip_digest", ipDigest(exchange));
        String subjectDigest = subjectDigest(exchange);
        if (subjectDigest != null) {
            event = event.addKeyValue("subject_digest", subjectDigest);
        }
        if (GatewayExchangeAttributes.entryChannel(exchange).orElse(null) == GatewayRouteChannel.CLIENT_API) {
            event = event.addKeyValue("channel", GatewayRouteChannel.CLIENT_API.name())
                .addKeyValue("profile", route == null ? "UNMATCHED" : route.securityProfileCode());
            Object clientDigest = exchange.getAttribute(GatewayExchangeAttributes.ACCESS_CLIENT_DIGEST);
            if (clientDigest instanceof String value && ACCESS_DIGEST_PATTERN.matcher(value).matches()) {
                event = event.addKeyValue("client_digest", value);
            }
        }
        String traceId = context.traceId();
        if (traceId != null) {
            event = event.addKeyValue("trace_id", traceId);
        }
        event.log("Gateway request completed");
    }

    private String routeCode(ServerWebExchange exchange) {
        String pinnedRouteCode =
            GatewayExchangeAttributes.runtimeRoute(exchange).map(route -> route.routeCode()).orElse(null);
        if (pinnedRouteCode != null) {
            return pinnedRouteCode;
        }
        String trustedRouteCode = trustedRouteCode(exchange);
        if (trustedRouteCode != null) {
            return trustedRouteCode;
        }
        return UNMATCHED_ROUTE;
    }

    private String trustedRouteCode(ServerWebExchange exchange) {
        Object value = exchange.getAttribute(GatewayExchangeAttributes.ROUTE_CODE);
        if (!(value instanceof String routeCode)) {
            return null;
        }
        return routeCode.matches("[A-Za-z][A-Za-z0-9._-]{0,127}") || UNMATCHED_ROUTE.equals(routeCode) ? routeCode
            : null;
    }

    private GatewayOutcome resolvedOutcome(ServerWebExchange exchange) {
        Object value = exchange.getAttribute(GatewayExchangeAttributes.OUTCOME);
        return value instanceof GatewayOutcome outcome ? outcome : GatewayOutcome.UPSTREAM_RESPONSE;
    }

    private int responseStatus(ServerWebExchange exchange) {
        HttpStatusCode status = exchange.getResponse().getStatusCode();
        return status == null ? 200 : status.value();
    }

    private String requestId(ServerWebExchange exchange) {
        Object value = exchange.getAttribute(GatewayExchangeAttributes.REQUEST_ID);
        return value instanceof String requestId && !requestId.isBlank() ? requestId : "unavailable";
    }

    private String ipDigest(ServerWebExchange exchange) {
        Object value = exchange.getAttribute(GatewayExchangeAttributes.CLIENT_ADDRESS);
        return value instanceof InetAddress address ? encoded(digester.accessIpDigest(address)) : "unavailable";
    }

    private String subjectDigest(ServerWebExchange exchange) {
        Object value = exchange.getAttribute(GatewayExchangeAttributes.ACCESS_SUBJECT_DIGEST);
        return value instanceof String subjectDigest && ACCESS_DIGEST_PATTERN.matcher(subjectDigest).matches()
            ? subjectDigest : null;
    }

    private String currentTraceId() {
        String traceId = MDC.get("trace_id");
        return traceId != null && TRACE_ID_PATTERN.matcher(traceId).matches() ? traceId : null;
    }

    private record TerminalStatus(String value, String family) {
        private static TerminalStatus http(int status) {
            return new TerminalStatus(Integer.toString(status), status / 100 + "xx");
        }

        private static TerminalStatus none() {
            return new TerminalStatus("NONE", "NONE");
        }
    }

    private final class TerminalContext {
        private final ServerWebExchange exchange;
        private final long startedAtNanos;
        private final String traceId;
        private final AtomicBoolean terminalEventWritten = new AtomicBoolean();

        private TerminalContext(ServerWebExchange exchange, long startedAtNanos) {
            this.exchange = exchange;
            this.startedAtNanos = startedAtNanos;
            this.traceId = currentTraceId();
        }

        private ServerWebExchange exchange() {
            return exchange;
        }

        private long startedAtNanos() {
            return startedAtNanos;
        }

        private String traceId() {
            return traceId;
        }

        private boolean claimTerminalEvent() {
            return terminalEventWritten.compareAndSet(false, true);
        }
    }

    private final class TerminalObservingResponse extends ServerHttpResponseDecorator {
        private final AtomicReference<TerminalContext> context = new AtomicReference<>();

        private TerminalObservingResponse(ServerHttpResponse delegate) {
            super(delegate);
        }

        private void attach(TerminalContext terminalContext) {
            if (context.compareAndSet(null, terminalContext)) {
                beforeCommit(() -> {
                    trustedResponseHeaders.protect(terminalContext.exchange());
                    return Mono.empty();
                });
            }
        }

        private TerminalContext detach() {
            return context.getAndSet(null);
        }
    }
}
