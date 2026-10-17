package com.paytech.gateway.routing;

import org.springframework.cloud.gateway.config.GlobalCorsProperties;
import org.springframework.cloud.gateway.handler.FilteringWebHandler;
import org.springframework.cloud.gateway.handler.RoutePredicateHandlerMapping;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.env.Environment;
import org.springframework.web.server.ServerWebExchange;

import com.paytech.gateway.web.GatewayExchangeAttributes;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** 将请求已固定的目标交给标准 Gateway 过滤器链，不再读取其他路由缓存。 */
public final class GatewayRequestRouteHandlerMapping extends RoutePredicateHandlerMapping {

    /** 保留框架管理监听、CORS 和过滤器处理，只替换业务 Route 的查找来源。 */
    public GatewayRequestRouteHandlerMapping(FilteringWebHandler webHandler, GlobalCorsProperties corsProperties,
        Environment environment) {
        super(webHandler, Flux::empty, corsProperties, environment);
    }

    /** 使用匹配时固定的 Target 构造本次传输路由；未匹配请求不能进入转发。 */
    @Override
    protected Mono<Route> lookupRoute(ServerWebExchange exchange) {
        return Mono.justOrEmpty(GatewayExchangeAttributes.runtimeRoute(exchange))
            .map(runtime -> Route.async().id(runtime.routeCode()).uri(runtime.target().routingUri())
                .asyncPredicate(ignored -> Mono.just(true)).build());
    }
}
