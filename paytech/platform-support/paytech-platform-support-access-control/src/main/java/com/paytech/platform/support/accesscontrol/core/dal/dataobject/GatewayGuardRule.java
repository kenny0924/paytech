package com.paytech.platform.support.accesscontrol.core.dal.dataobject;

import com.paytech.platform.support.api.enums.GatewayGuardDimension;
import com.paytech.platform.support.api.enums.GatewayGuardRuleType;

/**
 * Gateway Guard Rule 查询投影。
 */
public record GatewayGuardRule(
    /**
     * 拥有该 Guard Rule 的 Gateway Route 主键。
     */
    Long routeId,

    /**
     * 规则类型：令牌桶限流或并发限制。
     */
    GatewayGuardRuleType ruleType,

    /**
     * 运行时 Bucket/Lease 的隔离维度。
     */
    GatewayGuardDimension dimension,

    /**
     * RATE_LIMIT 令牌桶容量；CONCURRENCY 规则为空。
     */
    Long capacity,

    /**
     * RATE_LIMIT 每周期补充令牌数；CONCURRENCY 规则为空。
     */
    Long refillTokens,

    /**
     * RATE_LIMIT 补充周期毫秒数；CONCURRENCY 规则为空。
     */
    Long refillPeriodMillis,

    /**
     * CONCURRENCY 最大并发 Lease 数；RATE_LIMIT 规则为空。
     */
    Long concurrencyLimit) {
}
