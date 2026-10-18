package com.paytech.gateway.security;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;

import com.paytech.gateway.observability.GatewayOutcome;
import com.paytech.gateway.routing.runtime.GatewayRuntimeRoute;
import com.paytech.gateway.web.GatewayErrorCode;
import com.paytech.gateway.web.GatewayErrorWriter;
import com.paytech.gateway.web.GatewayExchangeAttributes;
import com.paytech.platform.support.api.enums.GatewayRouteChannel;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/** Operations 三条精确安全链中的前两条：匿名登录刷新与受保护登出。 */
@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
@EnableConfigurationProperties(OperationsSecurityProperties.class)
public class OperationsSecurityConfiguration {
    @Bean
    Clock operationsJwtClock() {
        return Clock.systemUTC();
    }

    @Bean
    Scheduler operationsJwksScheduler() {
        return Schedulers.parallel();
    }

    @Bean
    ReactiveJwtDecoder operationsReactiveJwtDecoder(ReactiveJwksProvider reactiveJwksProvider,
        OperationsSecurityProperties properties, Clock operationsJwtClock) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSource(reactiveJwksProvider::select)
            .jwsAlgorithm(SignatureAlgorithm.RS256).build();
        decoder.setJwtValidator(new OperationsJwtValidator(properties, operationsJwtClock));
        return token -> decoder.decode(token)
            .onErrorMap(exception -> hasCause(exception, JwksUnavailableException.class)
                ? new BadJwtException("Operations JWKS 暂时不可用", exception) : exception);
    }

    @Bean
    OperationsChannelAuthorizationManager operationsChannelAuthorizationManager(VersionedHmacDigester digester) {
        return new OperationsChannelAuthorizationManager(digester);
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    SecurityWebFilterChain operationsAnonymousSecurityWebFilterChain(ServerHttpSecurity http) {
        return standardDisabled(http.securityMatcher(routeMatcher("PUBLIC")))
            .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll()).build();
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 1)
    SecurityWebFilterChain operationsLogoutSecurityWebFilterChain(ServerHttpSecurity http,
        ReactiveJwtDecoder operationsReactiveJwtDecoder,
        OperationsChannelAuthorizationManager operationsChannelAuthorizationManager, GatewayErrorWriter errorWriter) {
        return standardDisabled(http.securityMatcher(routeMatcher("OPERATIONS_JWT")))
            .authorizeExchange(exchanges -> exchanges.pathMatchers(HttpMethod.OPTIONS, "/**").permitAll().anyExchange()
                .access(operationsChannelAuthorizationManager))
            .oauth2ResourceServer(
                resourceServer -> resourceServer.jwt(jwt -> jwt.jwtDecoder(operationsReactiveJwtDecoder))
                    .authenticationEntryPoint(
                        (exchange, exception) -> writeAuthenticationFailure(exchange, exception, errorWriter))
                    .accessDeniedHandler((exchange, exception) -> writeForbidden(exchange, errorWriter)))
            .build();
    }

    private ServerHttpSecurity standardDisabled(ServerHttpSecurity http) {
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable).httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable).logout(ServerHttpSecurity.LogoutSpec::disable);
    }

    private ServerWebExchangeMatcher routeMatcher(String securityProfileCode) {
        return exchange -> {
            boolean matches =
                GatewayExchangeAttributes.runtimeRoute(exchange).filter(route -> route.channel() == GatewayRouteChannel.OPERATIONS)
                    .map(GatewayRuntimeRoute::securityProfileCode).filter(securityProfileCode::equals).isPresent();
            return matches ? ServerWebExchangeMatcher.MatchResult.match()
                : ServerWebExchangeMatcher.MatchResult.notMatch();
        };
    }

    private Mono<Void> writeAuthenticationFailure(org.springframework.web.server.ServerWebExchange exchange,
        Throwable exception, GatewayErrorWriter errorWriter) {
        if (hasCause(exception, JwksUnavailableException.class)) {
            exchange.getAttributes().put(com.paytech.gateway.web.GatewayExchangeAttributes.OUTCOME,
                GatewayOutcome.DEPENDENCY_UNAVAILABLE);
            return errorWriter.write(exchange, HttpStatus.SERVICE_UNAVAILABLE,
                GatewayErrorCode.GATEWAY_DEPENDENCY_UNAVAILABLE, null);
        }
        exchange.getAttributes().put(com.paytech.gateway.web.GatewayExchangeAttributes.OUTCOME,
            GatewayOutcome.AUTHENTICATION_FAILED);
        return errorWriter.write(exchange, HttpStatus.UNAUTHORIZED, GatewayErrorCode.GATEWAY_UNAUTHORIZED, null);
    }

    private Mono<Void> writeForbidden(org.springframework.web.server.ServerWebExchange exchange,
        GatewayErrorWriter errorWriter) {
        exchange.getAttributes().put(com.paytech.gateway.web.GatewayExchangeAttributes.OUTCOME,
            GatewayOutcome.AUTHORIZATION_DENIED);
        return errorWriter.write(exchange, HttpStatus.FORBIDDEN, GatewayErrorCode.GATEWAY_FORBIDDEN, null);
    }

    private boolean hasCause(Throwable exception, Class<? extends Throwable> expectedType) {
        Throwable current = exception;
        while (current != null) {
            if (expectedType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
