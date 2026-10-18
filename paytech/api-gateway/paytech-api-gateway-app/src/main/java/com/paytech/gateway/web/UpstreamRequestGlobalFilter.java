package com.paytech.gateway.web;

import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.paytech.gateway.observability.GatewayOutcome;
import com.paytech.gateway.routing.runtime.GatewayRuntimeRoute;

import reactor.core.publisher.Mono;

/** 重建发送给 BFF 的最小可信请求 Header 与 Cookie 边界。 */
@Component
public class UpstreamRequestGlobalFilter implements GlobalFilter, Ordered {
    private final GatewayErrorWriter errorWriter;
    private final ConsoleRefreshCookiePolicy cookiePolicy;

    public UpstreamRequestGlobalFilter(GatewayErrorWriter errorWriter, ConsoleRefreshCookiePolicy cookiePolicy) {
        this.errorWriter = errorWriter;
        this.cookiePolicy = cookiePolicy;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange,
        org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
        GatewayRuntimeRoute route = GatewayExchangeAttributes.runtimeRoute(exchange).orElse(null);
        if (route == null) {
            return chain.filter(exchange);
        }
        String refreshCookie =
            requiresRefreshCookie(route) ? findSingleRefreshCookie(exchange.getRequest().getHeaders(), cookiePolicy.cookieName(route.channel())) : null;
        if (requiresRefreshCookie(route) && refreshCookie == null) {
            exchange.getAttributes().put(GatewayExchangeAttributes.OUTCOME, GatewayOutcome.AUTHENTICATION_FAILED);
            return errorWriter.write(exchange, HttpStatus.UNAUTHORIZED, GatewayErrorCode.GATEWAY_UNAUTHORIZED, null);
        }
        String downstreamPath = exchange.getRequiredAttribute(GatewayExchangeAttributes.DOWNSTREAM_PATH);
        ServerHttpRequest request = exchange.getRequest().mutate().path(downstreamPath)
            .headers(headers -> rebuildHeaders(exchange, headers, route, refreshCookie)).build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return GatewayGlobalFilterOrder.UPSTREAM_REQUEST;
    }

    private boolean requiresRefreshCookie(GatewayRuntimeRoute route) {
        return "REQUIRE_PROFILE_COOKIE".equals(route.requestCookieMode());
    }

    private String findSingleRefreshCookie(HttpHeaders headers, String cookieName) {
        if (cookieName == null) return null;
        String targetCookie = null;
        for (String cookieHeader : headers.getOrEmpty(HttpHeaders.COOKIE)) {
            for (String rawCookie : cookieHeader.split(";", -1)) {
                String candidate = rawCookie.trim();
                int separator = candidate.indexOf('=');
                String name = separator < 0 ? candidate : candidate.substring(0, separator);
                if (!cookieName.equals(name)) {
                    continue;
                }
                if (separator < 0 || targetCookie != null
                    || !isValidOpaqueCookieValue(candidate.substring(separator + 1))) {
                    return null;
                }
                targetCookie = candidate;
            }
        }
        return targetCookie;
    }

    private boolean isValidOpaqueCookieValue(String value) {
        if (value.isEmpty()) {
            return false;
        }
        if (value.charAt(0) == '"') {
            return value.length() > 2 && value.charAt(value.length() - 1) == '"'
                && hasOnlyCookieOctets(value, 1, value.length() - 1);
        }
        return hasOnlyCookieOctets(value, 0, value.length());
    }

    private boolean hasOnlyCookieOctets(String value, int start, int end) {
        for (int index = start; index < end; index++) {
            char current = value.charAt(index);
            if (current != 0x21 && !(current >= 0x23 && current <= 0x2B) && !(current >= 0x2D && current <= 0x3A)
                && !(current >= 0x3C && current <= 0x5B) && !(current >= 0x5D && current <= 0x7E)) {
                return false;
            }
        }
        return true;
    }

    private void rebuildHeaders(ServerWebExchange exchange, HttpHeaders headers, GatewayRuntimeRoute route,
        String refreshCookie) {
        HttpHeaders trustedHeaders = new HttpHeaders();
        route.forwardedRequestHeaders().forEach(name -> copyFirst(headers, trustedHeaders, name));
        trustedHeaders.set(GatewayProtocol.REQUEST_ID,
            exchange.getRequiredAttribute(GatewayExchangeAttributes.REQUEST_ID));
        if (route.channel() != com.paytech.platform.support.api.enums.GatewayRouteChannel.CLIENT_API) {
            trustedHeaders.set(GatewayProtocol.ROUTE_CODE, route.routeCode());
        }
        if (refreshCookie != null) {
            trustedHeaders.set(HttpHeaders.COOKIE, refreshCookie);
        }
        long actualBodyLength = exchange.getRequiredAttribute(GatewayExchangeAttributes.ACTUAL_REQUEST_BODY_LENGTH);
        trustedHeaders.setContentLength(actualBodyLength);
        trustedHeaders.remove(HttpHeaders.TRANSFER_ENCODING);
        headers.clear();
        headers.putAll(trustedHeaders);
    }

    private void copyFirst(HttpHeaders source, HttpHeaders target, String name) {
        String value = source.getFirst(name);
        if (value != null) {
            target.set(name, value);
        }
    }
}
