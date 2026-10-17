package com.paytech.gateway.idempotency;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Gateway 请求幂等配置。
 *
 * @param retention 记录自首次被接受起的保留时长
 * @param pendingLeaseExtra 待定租约在路由响应超时与请求体接收超时之外的附加量
 * @param maxResponseBytes 可记录的最大下游响应体字节数
 * @param redis Redis 存储配置；未配置 Namespace 时沿用 Guard Namespace
 */
@ConfigurationProperties("gateway.idempotency")
public record IdempotencyProperties(@DefaultValue("72h") Duration retention,
    @DefaultValue("10s") Duration pendingLeaseExtra, @DefaultValue("65536") int maxResponseBytes,
    @DefaultValue Redis redis) {

    public IdempotencyProperties {
        if (retention == null || retention.isNegative() || retention.isZero()) {
            throw new IllegalArgumentException("请求幂等记录保留时长必须为正");
        }
        if (pendingLeaseExtra == null || pendingLeaseExtra.isNegative()) {
            throw new IllegalArgumentException("请求幂等待定租约附加量不可为负");
        }
        if (maxResponseBytes <= 0) {
            throw new IllegalArgumentException("请求幂等可记录响应体上限必须为正");
        }
        redis = redis == null ? new Redis(null) : redis;
    }

    /** 路由级待定租约：上游响应超时 + 请求体接收超时 + 附加量。 */
    public Duration pendingLease(Duration responseTimeout, Duration bodyReceiveTimeout) {
        return responseTimeout.plus(bodyReceiveTimeout).plus(pendingLeaseExtra);
    }

    /**
     * @param namespace 幂等记录 Redis Key 前缀；为空时沿用 Guard Namespace
     */
    public record Redis(String namespace) {}
}
