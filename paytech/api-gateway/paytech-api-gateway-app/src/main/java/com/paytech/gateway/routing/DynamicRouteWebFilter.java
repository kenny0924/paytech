package com.paytech.gateway.routing;

import java.util.Objects;

import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import com.paytech.gateway.observability.GatewayOutcome;
import com.paytech.gateway.routing.runtime.GatewayRouteMatch;
import com.paytech.gateway.routing.runtime.GatewayRuntimeRoute;
import com.paytech.gateway.routing.snapshot.GatewaySnapshotStore;
import com.paytech.gateway.web.GatewayErrorCode;
import com.paytech.gateway.web.GatewayErrorWriter;
import com.paytech.gateway.web.GatewayExchangeAttributes;
import com.paytech.gateway.web.GatewayGlobalFilterOrder;
import com.paytech.platform.support.api.enums.GatewayRouteChannel;

import reactor.core.publisher.Mono;

/** 在所有公共策略执行前从同一不可变 Snapshot 捕获并固定一条运行时 Route。 */
@Component
public final class DynamicRouteWebFilter implements WebFilter, Ordered {
    private final GatewaySnapshotStore snapshotStore;
    private final GatewayErrorWriter errorWriter;

    public DynamicRouteWebFilter(GatewaySnapshotStore snapshotStore, GatewayErrorWriter errorWriter) {
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "Gateway Snapshot Store 不可为空");
        this.errorWriter = Objects.requireNonNull(errorWriter, "Gateway Error Writer 不可为空");
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!exchange.getAttributes().containsKey(GatewayExchangeAttributes.CLIENT_ADDRESS)) {
            return chain.filter(exchange);
        }
        GatewayRouteMatch match = snapshotStore.current().flatMap(
            snapshot -> snapshot.catalog().match(GatewayExchangeAttributes.entryChannel(exchange).orElse(null),
                routeMethod(exchange), exchange.getRequest().getURI().getRawPath()))
            .orElse(null);
        if (match == null) {
            return writeRouteNotFound(exchange);
        }
        GatewayRuntimeRoute route = match.route();
        exchange.getAttributes().put(GatewayExchangeAttributes.RUNTIME_ROUTE, route);
        exchange.getAttributes().put(GatewayExchangeAttributes.DOWNSTREAM_PATH, match.downstreamPath());
        exchange.getAttributes().put(GatewayExchangeAttributes.ROUTE_CODE, route.routeCode());
        return chain.filter(exchange);
    }

    private HttpMethod routeMethod(ServerWebExchange exchange) {
        GatewayRouteChannel channel = GatewayExchangeAttributes.entryChannel(exchange).orElse(null);
        if ((channel != GatewayRouteChannel.OPERATIONS && channel != GatewayRouteChannel.CLIENT)
            || exchange.getRequest().getMethod() != HttpMethod.OPTIONS) {
            return exchange.getRequest().getMethod();
        }
        java.util.List<String> values =
            exchange.getRequest().getHeaders().getOrEmpty(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD);
        if (values.size() != 1) {
            return HttpMethod.OPTIONS;
        }
        try {
            return HttpMethod.valueOf(values.getFirst());
        } catch (IllegalArgumentException exception) {
            return HttpMethod.OPTIONS;
        }
    }

    @Override
    public int getOrder() {
        return GatewayGlobalFilterOrder.DYNAMIC_ROUTE;
    }

    /** 未匹配请求不读取未知请求体，返回错误后关闭连接，避免入站数据阻塞后续请求。 */
    private Mono<Void> writeRouteNotFound(ServerWebExchange exchange) {
        exchange.getAttributes().put(GatewayExchangeAttributes.ROUTE_CODE, "UNMATCHED");
        exchange.getAttributes().put(GatewayExchangeAttributes.OUTCOME, GatewayOutcome.ROUTE_NOT_FOUND);
        exchange.getResponse().getHeaders().set(HttpHeaders.CONNECTION, "close");
        return errorWriter.write(exchange, HttpStatus.NOT_FOUND, GatewayErrorCode.GATEWAY_ROUTE_NOT_FOUND, null);
    }
}
