package com.paytech.gateway.routing.snapshot;

import com.paytech.platform.support.api.gatewayconfiguration.response.GatewayPublicSnapshotResponseBody;

import reactor.core.publisher.Mono;

/** PLT-I20 私有控制面客户端；不得用于浏览器请求。 */
@FunctionalInterface
public interface ReactiveGatewaySnapshotClient {
    Mono<GatewayPublicSnapshotResponseBody> fetch();
}
