package com.paytech.gateway.security.identity;

import java.util.Objects;
import java.util.Optional;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.paytech.gateway.routing.runtime.GatewayRuntimeRoute;
import com.paytech.gateway.security.VersionedHmacDigester;
import com.paytech.gateway.security.VersionedHmacDigester.VersionedDigest;
import com.paytech.gateway.security.clientapi.ClientApiAuthenticationToken;
import com.paytech.gateway.security.clientapi.ClientApiIdentity;
import com.paytech.gateway.web.GatewayExchangeAttributes;
import com.paytech.gateway.web.GatewayGlobalFilterOrder;
import com.paytech.platform.support.api.enums.GatewayRouteChannel;

import reactor.core.publisher.Mono;

/** 在认证完成后建立内部 typed identity，不修改下游请求 Header。 */
@Component
public final class GatewayAuthenticatedIdentityGlobalFilter implements GlobalFilter, Ordered {
    private final VersionedHmacDigester digester;

    public GatewayAuthenticatedIdentityGlobalFilter(VersionedHmacDigester digester) {
        this.digester = Objects.requireNonNull(digester, "访问日志摘要器不可为空");
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (GatewayExchangeAttributes.runtimeRoute(exchange)
            .filter(route -> route.channel() == GatewayRouteChannel.CLIENT_API).isPresent()) {
            return ReactiveSecurityContextHolder.getContext()
                .flatMap(context -> Mono.justOrEmpty(context.getAuthentication()))
                .filter(authentication -> authentication instanceof ClientApiAuthenticationToken && authentication.isAuthenticated())
                .cast(ClientApiAuthenticationToken.class).doOnNext(token -> record(exchange, token.getPrincipal()))
                .then(Mono.defer(() -> chain.filter(exchange)));
        }
        if (GatewayExchangeAttributes.runtimeRoute(exchange)
            .filter(route -> route.channel() == GatewayRouteChannel.CLIENT && "CLIENT_JWT".equals(route.securityProfileCode())).isPresent()) {
            return ReactiveSecurityContextHolder.getContext()
                .flatMap(context -> Mono.justOrEmpty(context.getAuthentication()))
                .filter(authentication -> authentication instanceof JwtAuthenticationToken && authentication.isAuthenticated())
                .cast(JwtAuthenticationToken.class).doOnNext(authentication -> {
                    var jwt = authentication.getToken();
                    record(exchange, new ClientConsoleIdentity(jwt.getSubject(), jwt.getClaimAsString("sid"),
                        jwt.getClaimAsString("context_type"), jwt.getClaimAsString("tenant_id"),
                        jwt.getClaimAsString("tenant_member_id")));
                }).then(Mono.defer(() -> chain.filter(exchange)));
        }
        if (!isOperationsJwtRoute(exchange)) {
            return chain.filter(exchange);
        }
        return ReactiveSecurityContextHolder.getContext()
            .flatMap(context -> Mono.justOrEmpty(context.getAuthentication()))
            .filter(authentication -> authentication instanceof JwtAuthenticationToken && authentication.isAuthenticated())
            .cast(JwtAuthenticationToken.class).map(authentication -> {
                operationsIdentity(authentication).ifPresent(identity -> record(exchange, identity));
                return exchange;
            }).defaultIfEmpty(exchange).flatMap(chain::filter);
    }

    @Override
    public int getOrder() {
        return GatewayGlobalFilterOrder.TYPED_AUTHENTICATED_IDENTITY;
    }

    private boolean isOperationsJwtRoute(ServerWebExchange exchange) {
        return GatewayExchangeAttributes.runtimeRoute(exchange)
            .filter(route -> route.channel() == GatewayRouteChannel.OPERATIONS)
            .map(GatewayRuntimeRoute::securityProfileCode).filter("OPERATIONS_JWT"::equals).isPresent();
    }

    private Optional<OperationsIdentity> operationsIdentity(JwtAuthenticationToken authentication) {
        try {
            return Optional.of(new OperationsIdentity(authentication.getToken().getSubject(),
                authentication.getToken().getClaimAsString("sid"),
                authentication.getToken().getClaimAsString("context_type"),
                authentication.getToken().getClaimAsString("tenant_id"),
                authentication.getToken().getClaimAsString("tenant_member_id")));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private void record(ServerWebExchange exchange, GatewayAuthenticatedIdentity identity) {
        exchange.getAttributes().put(GatewayExchangeAttributes.AUTHENTICATED_IDENTITY, identity);
        VersionedDigest digest;
        if (identity instanceof ClientApiIdentity client) {
            VersionedDigest clientDigest = digester.accessClientDigest(client.clientId());
            exchange.getAttributes().put(GatewayExchangeAttributes.ACCESS_CLIENT_DIGEST,
                clientDigest.version() + ":" + clientDigest.digest());
            digest = digester.accessCredentialDigest(client.credentialId());
        } else {
            digest = digester.accessSubjectDigest(identity.accessSubject());
        }
        exchange.getAttributes().putIfAbsent(GatewayExchangeAttributes.ACCESS_SUBJECT_DIGEST,
            digest.version() + ":" + digest.digest());
    }
}
