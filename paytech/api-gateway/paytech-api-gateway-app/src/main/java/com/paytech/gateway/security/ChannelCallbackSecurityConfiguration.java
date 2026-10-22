package com.paytech.gateway.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.web.server.ServerWebExchange;

import com.paytech.gateway.web.GatewayExchangeAttributes;
import com.paytech.platform.support.api.enums.GatewayAuthorizationMode;
import com.paytech.platform.support.api.enums.GatewayRouteChannel;
import com.paytech.platform.support.api.enums.GatewaySecurityProfileCode;

/**
 * 渠道支付通知入口的无认证安全链。
 *
 * <p>渠道服务器不持有平台凭据，Gateway 只负责入口、Guard、Body 上限与转发；渠道签名由 Owner（channel-connector）
 * 使用原始 Body 校验，因此本链不读取、不改写 Body。只匹配运行时 Snapshot 中 CHANNEL_CALLBACK 渠道、档案与授权模式
 * 三者一致的路由，未登记路径仍落到兜底拒绝链。
 */
@Configuration(proxyBeanMethods = false)
public class ChannelCallbackSecurityConfiguration {
    /** 已登记的渠道回调路由放行，不建立会话、不缓存请求。 */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 5)
    SecurityWebFilterChain channelCallbackSecurityWebFilterChain(ServerHttpSecurity http) {
        return http.securityMatcher(exchange -> matches(exchange) ? ServerWebExchangeMatcher.MatchResult.match()
                : ServerWebExchangeMatcher.MatchResult.notMatch())
            .csrf(ServerHttpSecurity.CsrfSpec::disable).httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable).logout(ServerHttpSecurity.LogoutSpec::disable)
            .requestCache(cache -> cache.requestCache(NoOpServerRequestCache.getInstance()))
            .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
            .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll()).build();
    }

    /** 当前请求固定的运行时路由是否为渠道回调路由。 */
    static boolean matches(ServerWebExchange exchange) {
        return GatewayExchangeAttributes.runtimeRoute(exchange)
            .filter(route -> route.channel() == GatewayRouteChannel.CHANNEL_CALLBACK)
            .filter(route -> GatewaySecurityProfileCode.CHANNEL_CALLBACK.getCode().equals(route.securityProfileCode()))
            .filter(route -> GatewayAuthorizationMode.CHANNEL_CALLBACK.getCode().equals(route.authorizationMode()))
            .isPresent();
    }
}
