package com.paytech.gateway.routing.snapshot;

import java.time.Duration;

import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import com.paytech.platform.support.api.gatewayconfiguration.response.GatewayPublicSnapshotResponseBody;

import reactor.core.publisher.Mono;

/** 受限 WebClient PLT-I20 适配器，不发送身份、调用方或条件缓存 Header。 */
public final class PlatformGatewaySnapshotClient implements ReactiveGatewaySnapshotClient {
    private final WebClient webClient;
    private final Duration responseTimeout;

    public PlatformGatewaySnapshotClient(WebClient webClient, Duration responseTimeout) {
        this.webClient = webClient;
        this.responseTimeout = responseTimeout;
    }

    @Override
    public Mono<GatewayPublicSnapshotResponseBody> fetch() {
        return webClient.get().uri("/platform-support/gateway-config/v1/public-snapshot")
            .accept(MediaType.APPLICATION_JSON).exchangeToMono(response -> {
                if (response.statusCode().value() != 200) {
                    return response.releaseBody().then(Mono.error(new UnexpectedSnapshotResponseException()));
                }
                return response.bodyToMono(GatewayPublicSnapshotResponseBody.class);
            }).timeout(responseTimeout);
    }

    private static final class UnexpectedSnapshotResponseException extends RuntimeException {}
}
