package com.paytech.gateway.web;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import com.paytech.gateway.observability.GatewayOutcome;
import com.paytech.platform.support.api.enums.GatewayRouteChannel;

import io.netty.util.NetUtil;
import reactor.core.publisher.Mono;

/** 清理调用方伪造的保留 Header，并创建可信请求标识。 */
@Component
public class TrustedRequestContextWebFilter implements WebFilter, Ordered {
    private final GatewayErrorWriter errorWriter;
    private final Environment environment;
    private final GatewayRequestIdPolicy requestIdPolicy =
        new GatewayRequestIdPolicy(() -> UUID.randomUUID().toString());

    public TrustedRequestContextWebFilter(GatewayErrorWriter errorWriter, Environment environment) {
        this.errorWriter = Objects.requireNonNull(errorWriter, "Gateway 错误写入器不可为空");
        this.environment = Objects.requireNonNull(environment, "Spring Environment 不可为空");
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        boolean managementRequest = isManagementRequest(exchange);
        GatewayRouteChannel channel = managementRequest ? null : trustedEntryChannel(exchange);
        if (channel != null) {
            exchange.getAttributes().put(GatewayExchangeAttributes.ENTRY_CHANNEL, channel);
        }
        String requestId =
            requestIdPolicy.resolve(channel, exchange.getRequest().getHeaders().get(GatewayProtocol.REQUEST_ID));
        exchange.getAttributes().put(GatewayExchangeAttributes.REQUEST_ID, requestId);
        exchange.getResponse().getHeaders().set(GatewayProtocol.REQUEST_ID, requestId);
        InetAddress clientAddress = managementRequest || channel == null ? null : trustedClientAddress(exchange);
        ServerHttpRequest request = exchange.getRequest().mutate().headers(headers -> headers.headerNames().stream()
            .filter(this::isCallerControlledHeader).toList().forEach(headers::remove)).build();
        ServerWebExchange trustedExchange = exchange.mutate().request(request).build();
        if (!managementRequest && (channel == null || clientAddress == null)) {
            trustedExchange.getAttributes().put(GatewayExchangeAttributes.OUTCOME, GatewayOutcome.AUTHORIZATION_DENIED);
            return errorWriter.write(trustedExchange, HttpStatus.FORBIDDEN, GatewayErrorCode.GATEWAY_FORBIDDEN, null);
        }
        if (managementRequest) {
            return chain.filter(trustedExchange);
        }
        trustedExchange.getAttributes().put(GatewayExchangeAttributes.CLIENT_ADDRESS, clientAddress);
        return chain.filter(trustedExchange);
    }

    @Override
    public int getOrder() {
        return GatewayGlobalFilterOrder.TRUSTED_REQUEST_CONTEXT;
    }

    private GatewayRouteChannel trustedEntryChannel(ServerWebExchange exchange) {
        List<String> values = exchange.getRequest().getHeaders().get(GatewayProtocol.ENTRY_CHANNEL);
        if (values == null || values.size() != 1) {
            return null;
        }
        for (GatewayRouteChannel channel : GatewayRouteChannel.values()) {
            if (channel.getCode().equals(values.getFirst())) {
                return channel;
            }
        }
        return null;
    }

    private boolean isCallerControlledHeader(String headerName) {
        return GatewayProtocol.isReservedIngressHeader(headerName);
    }

    private boolean isManagementRequest(ServerWebExchange exchange) {
        InetSocketAddress localAddress = exchange.getRequest().getLocalAddress();
        if (localAddress == null) {
            return false;
        }
        Integer managementPort = environment.getProperty("local.management.port", Integer.class);
        if (managementPort == null || managementPort <= 0) {
            managementPort = environment.getProperty("management.server.port", Integer.class);
        }
        return managementPort != null && managementPort > 0 && localAddress.getPort() == managementPort;
    }

    private InetAddress trustedClientAddress(ServerWebExchange exchange) {
        List<String> values = exchange.getRequest().getHeaders().get(GatewayProtocol.CLIENT_IP);
        if (values == null || values.size() != 1) {
            return null;
        }
        String value = values.getFirst();
        if (value == null || value.isEmpty() || value.indexOf(',') >= 0 || value.indexOf('%') >= 0
            || value.indexOf('[') >= 0 || value.indexOf(']') >= 0) {
            return null;
        }
        byte[] bytes = NetUtil.createByteArrayFromIpAddressString(value);
        if (bytes == null) {
            return null;
        }
        try {
            return InetAddress.getByAddress(bytes);
        } catch (UnknownHostException exception) {
            return null;
        }
    }
}
