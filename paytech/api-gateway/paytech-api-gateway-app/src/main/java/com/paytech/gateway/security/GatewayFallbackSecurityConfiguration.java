package com.paytech.gateway.security;

import org.springframework.boot.micrometer.metrics.autoconfigure.export.prometheus.PrometheusScrapeEndpoint;
import org.springframework.boot.security.autoconfigure.actuate.web.reactive.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.OrServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.server.ServerWebExchange;

import com.paytech.gateway.observability.GatewayOutcome;
import com.paytech.gateway.web.GatewayErrorCode;
import com.paytech.gateway.web.GatewayErrorWriter;
import com.paytech.gateway.web.GatewayExchangeAttributes;

import reactor.core.publisher.Mono;

/** 为未登记路径保留拒绝优先的最终安全链。 */
@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
public class GatewayFallbackSecurityConfiguration {
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    SecurityWebFilterChain managementProbeSecurityWebFilterChain(ServerHttpSecurity http) {
        var publicManagementEndpoints =
            new OrServerWebExchangeMatcher(EndpointRequest.to(PrometheusScrapeEndpoint.class),
                ServerWebExchangeMatchers.pathMatchers("/actuator/health/liveness", "/actuator/health/readiness"));
        return standardDisabled(http.securityMatcher(publicManagementEndpoints))
            .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll()).build();
    }

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    SecurityWebFilterChain fallbackSecurityWebFilterChain(ServerHttpSecurity http, GatewayErrorWriter errorWriter) {
        return standardDisabled(http.authorizeExchange(exchanges -> exchanges.anyExchange().denyAll()))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((exchange, failure) -> reject(exchange, errorWriter, HttpStatus.UNAUTHORIZED,
                    GatewayErrorCode.GATEWAY_UNAUTHORIZED, GatewayOutcome.AUTHENTICATION_FAILED))
                .accessDeniedHandler((exchange, failure) -> reject(exchange, errorWriter, HttpStatus.FORBIDDEN,
                    GatewayErrorCode.GATEWAY_FORBIDDEN, GatewayOutcome.AUTHORIZATION_DENIED)))
            .build();
    }

    private Mono<Void> reject(ServerWebExchange exchange, GatewayErrorWriter errorWriter, HttpStatus status,
        GatewayErrorCode code, GatewayOutcome outcome) {
        exchange.getAttributes().put(GatewayExchangeAttributes.OUTCOME, outcome);
        return errorWriter.write(exchange, status, code, null);
    }

    private ServerHttpSecurity standardDisabled(ServerHttpSecurity http) {
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable).httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable).logout(ServerHttpSecurity.LogoutSpec::disable);
    }

}
