package com.paytech.gateway.observability;

import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.ReactiveHealthIndicator;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

/** 以短超时 Reactive PING 检查 Gateway Guard Redis，不暴露连接细节。 */
@Component("gatewayRedis")
public final class GatewayRedisReactiveHealthIndicator implements ReactiveHealthIndicator {
    private static final Duration PING_TIMEOUT = Duration.ofMillis(250);

    private final ReactiveRedisConnectionFactory connectionFactory;

    public GatewayRedisReactiveHealthIndicator(ReactiveRedisConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory, "Redis 连接工厂不可为空");
    }

    private static Health available() {
        return Health.up().withDetail("state", "reachable").build();
    }

    private static Health unavailable() {
        return Health.down().withDetail("state", "unavailable").build();
    }

    @Override
    public Mono<Health> health() {
        return Mono.usingWhen(Mono.fromSupplier(connectionFactory::getReactiveConnection), this::ping,
            ReactiveRedisConnection::closeLater, (connection, error) -> connection.closeLater(),
            ReactiveRedisConnection::closeLater).onErrorReturn(unavailable());
    }

    private Mono<Health> ping(ReactiveRedisConnection connection) {
        return connection.ping().timeout(PING_TIMEOUT).filter(response -> "PONG".equalsIgnoreCase(response))
            .map(ignored -> available())
            .switchIfEmpty(Mono.fromSupplier(GatewayRedisReactiveHealthIndicator::unavailable));
    }
}
