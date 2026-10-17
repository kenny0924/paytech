package com.paytech.platform.support.api.gatewayconfiguration.response;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 路由限流或并发保护的配置值，不包含 Redis 运行状态。 */
public final class GatewayGuardRuleSnapshot {

    /**
     * Guard 规则类型编码。
     * <li>RATE_LIMIT 表示令牌桶限流，CONCURRENCY 表示并发限制。</li>
     */
    private final String ruleType;

    /**
     * Guard 计数维度编码。
     * <li>按安全档案选择 SHARED_ROUTE、CLIENT_IP、AUTHENTICATED_PRINCIPAL 或 TENANT。</li>
     */
    private final String dimension;

    /**
     * 令牌桶容量。
     * <li>RATE_LIMIT 时为正数；CONCURRENCY 时为空。</li>
     */
    private final Long capacity;

    /**
     * 每个补充周期增加的令牌数。
     * <li>RATE_LIMIT 时为正数；CONCURRENCY 时为空。</li>
     */
    private final Long refillTokens;

    /**
     * 令牌补充周期。
     * <li>单位为毫秒；RATE_LIMIT 时为正数，CONCURRENCY 时为空。</li>
     */
    private final Long refillPeriodMillis;

    /**
     * 允许的并发请求数。
     * <li>CONCURRENCY 时为正数；RATE_LIMIT 时为空。</li>
     */
    private final Long concurrencyLimit;

    /** 绑定 Guard 类型、维度与数值配置。 */
    @JsonCreator
    public GatewayGuardRuleSnapshot(@JsonProperty("ruleType") String ruleType,
        @JsonProperty("dimension") String dimension,
        @JsonProperty("capacity") Long capacity,
        @JsonProperty("refillTokens") Long refillTokens,
        @JsonProperty("refillPeriodMillis") Long refillPeriodMillis,
        @JsonProperty("concurrencyLimit") Long concurrencyLimit) {
        this.ruleType = ruleType;
        this.dimension = dimension;
        this.capacity = capacity;
        this.refillTokens = refillTokens;
        this.refillPeriodMillis = refillPeriodMillis;
        this.concurrencyLimit = concurrencyLimit;
    }

    /** 返回Guard 规则类型编码。 */
    public String getRuleType() {
        return ruleType;
    }

    /** 返回Guard 计数维度编码。 */
    public String getDimension() {
        return dimension;
    }

    /** 返回令牌桶容量。 */
    public Long getCapacity() {
        return capacity;
    }

    /** 返回每个补充周期增加的令牌数。 */
    public Long getRefillTokens() {
        return refillTokens;
    }

    /** 返回令牌补充周期。 */
    public Long getRefillPeriodMillis() {
        return refillPeriodMillis;
    }

    /** 返回允许的并发请求数。 */
    public Long getConcurrencyLimit() {
        return concurrencyLimit;
    }

    /** 比较完整快照字段，保持原契约的值相等语义。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GatewayGuardRuleSnapshot value)) {
            return false;
        }
        return Objects.equals(ruleType, value.ruleType)
            && Objects.equals(dimension, value.dimension)
            && Objects.equals(capacity, value.capacity)
            && Objects.equals(refillTokens, value.refillTokens)
            && Objects.equals(refillPeriodMillis, value.refillPeriodMillis)
            && Objects.equals(concurrencyLimit, value.concurrencyLimit);
    }

    /** 根据完整快照字段生成与值相等语义一致的哈希值。 */
    @Override
    public int hashCode() {
        return Objects.hash(ruleType, dimension, capacity, refillTokens, refillPeriodMillis, concurrencyLimit);
    }
}
