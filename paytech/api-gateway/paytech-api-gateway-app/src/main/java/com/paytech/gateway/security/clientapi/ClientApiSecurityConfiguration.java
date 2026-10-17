package com.paytech.gateway.security.clientapi;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.web.server.ServerWebExchange;

import com.paytech.gateway.observability.GatewayOutcome;
import com.paytech.gateway.web.GatewayErrorCode;
import com.paytech.gateway.web.GatewayErrorWriter;
import com.paytech.gateway.web.GatewayExchangeAttributes;
import com.paytech.platform.support.api.enums.GatewayRouteChannel;

import reactor.core.publisher.Mono;

/** Client API 专属无 Session 安全链，绝不回退到 Operations JWT 或匿名认证。 */
@Configuration(proxyBeanMethods = false)
public class ClientApiSecurityConfiguration {
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 2)
    SecurityWebFilterChain clientApiSecurityWebFilterChain(ServerHttpSecurity http,
        ReactiveClientApiCredentialValidationClient client, GatewayErrorWriter errorWriter) {
        AuthenticationWebFilter authentication = new AuthenticationWebFilter(
            new ClientApiCredentialReactiveAuthenticationManager(client));
        authentication.setServerAuthenticationConverter(new ClientApiCredentialServerAuthenticationConverter());
        authentication.setSecurityContextRepository(NoOpServerSecurityContextRepository.getInstance());
        authentication.setAuthenticationFailureHandler((exchange, error) -> failure(exchange.getExchange(), error, errorWriter));
        authentication.setAuthenticationSuccessHandler((exchange, result) -> exchange.getChain().filter(
            exchange.getExchange().mutate().request(request -> request.headers(headers -> headers.remove(HttpHeaders.AUTHORIZATION)))
                .build()));
        return http.securityMatcher(exchange -> matches(exchange) ? ServerWebExchangeMatcher.MatchResult.match()
                : ServerWebExchangeMatcher.MatchResult.notMatch())
            .csrf(ServerHttpSecurity.CsrfSpec::disable).httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable).logout(ServerHttpSecurity.LogoutSpec::disable)
            .requestCache(cache -> cache.requestCache(NoOpServerRequestCache.getInstance()))
            .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
            .exceptionHandling(errors -> errors.authenticationEntryPoint((exchange, error) -> failure(exchange, error, errorWriter))
                .accessDeniedHandler((exchange, error) -> failure(exchange, error, errorWriter)))
            .authorizeExchange(exchanges -> exchanges.anyExchange().authenticated())
            .addFilterAt(authentication, SecurityWebFiltersOrder.AUTHENTICATION).build();
    }

    static boolean matches(ServerWebExchange exchange) {
        return GatewayExchangeAttributes.runtimeRoute(exchange)
            .filter(route -> route.channel() == GatewayRouteChannel.CLIENT_API)
            .filter(route -> "CLIENT_API_CREDENTIAL".equals(route.securityProfileCode()))
            .filter(route -> "API_SCOPE".equals(route.authorizationMode())).isPresent();
    }

    private Mono<Void> failure(ServerWebExchange exchange, Throwable error, GatewayErrorWriter writer) {
        boolean unavailable = error instanceof ClientApiCredentialDependencyUnavailableException;
        exchange.getAttributes().put(GatewayExchangeAttributes.OUTCOME,
            unavailable ? GatewayOutcome.DEPENDENCY_UNAVAILABLE : GatewayOutcome.AUTHENTICATION_FAILED);
        return writer.write(exchange, unavailable ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.UNAUTHORIZED,
            unavailable ? GatewayErrorCode.GATEWAY_DEPENDENCY_UNAVAILABLE : GatewayErrorCode.GATEWAY_UNAUTHORIZED, null);
    }
}
