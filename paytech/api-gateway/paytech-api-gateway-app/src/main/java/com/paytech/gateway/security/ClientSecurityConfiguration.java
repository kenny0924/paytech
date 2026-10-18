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

/** CLIENT 公共认证与受保护账号入口；不执行核心资源授权。 */
@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
@EnableConfigurationProperties(ClientSecurityProperties.class)
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="gateway.client.enabled", havingValue="true")
public class ClientSecurityConfiguration {
    public ClientSecurityConfiguration(ClientSecurityProperties client, OperationsSecurityProperties operations) {
        if (client.issuer().equals(operations.issuer())) {
            throw new IllegalArgumentException("CUSTOMER 与 OPERATIONS 必须使用不同 issuer");
        }
    }
    /** HTTP 传输复用私网 Platform 连接，CUSTOMER 公钥缓存独立。 */
    @Bean(defaultCandidate=false)
    ReactiveJwksProvider customerJwksProvider(
        @org.springframework.beans.factory.annotation.Qualifier("operationsJwksWebClient") org.springframework.web.reactive.function.client.WebClient client,
        Clock clock, @org.springframework.beans.factory.annotation.Qualifier("operationsJwksScheduler") Scheduler scheduler,
        com.paytech.gateway.observability.GatewayMetrics metrics) {
        return new ReactiveJwksProvider(client, clock, scheduler, metrics, "CUSTOMER");
    }

    @Bean(defaultCandidate=false)
    ReactiveJwtDecoder clientReactiveJwtDecoder(@org.springframework.beans.factory.annotation.Qualifier("customerJwksProvider") ReactiveJwksProvider reactiveJwksProvider,
        ClientSecurityProperties properties, Clock operationsJwtClock) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSource(reactiveJwksProvider::select)
            .jwsAlgorithm(SignatureAlgorithm.RS256).build();
        decoder.setJwtValidator(new ClientJwtValidator(properties, operationsJwtClock));
        return token -> decoder.decode(token)
            .onErrorMap(exception -> hasCause(exception, JwksUnavailableException.class)
                ? new BadJwtException("CUSTOMER JWKS 暂时不可用", exception) : exception);
    }

    @Bean
    ClientChannelAuthorizationManager clientChannelAuthorizationManager(VersionedHmacDigester digester) {
        return new ClientChannelAuthorizationManager(digester);
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 3)
    SecurityWebFilterChain clientAnonymousSecurityWebFilterChain(ServerHttpSecurity http) {
        return standardDisabled(http.securityMatcher(routeMatcher("PUBLIC")))
            .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll()).build();
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 4)
    SecurityWebFilterChain clientProtectedSecurityWebFilterChain(ServerHttpSecurity http,
        @org.springframework.beans.factory.annotation.Qualifier("clientReactiveJwtDecoder") ReactiveJwtDecoder clientReactiveJwtDecoder,
        ClientChannelAuthorizationManager clientChannelAuthorizationManager, GatewayErrorWriter errorWriter) {
        return standardDisabled(http.securityMatcher(routeMatcher("CLIENT_JWT")))
            .authorizeExchange(exchanges -> exchanges.pathMatchers(HttpMethod.OPTIONS, "/**").permitAll().anyExchange()
                .access(clientChannelAuthorizationManager))
            .oauth2ResourceServer(
                resourceServer -> resourceServer.jwt(jwt -> jwt.jwtDecoder(clientReactiveJwtDecoder))
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
                GatewayExchangeAttributes.runtimeRoute(exchange).filter(route -> route.channel() == GatewayRouteChannel.CLIENT)
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
