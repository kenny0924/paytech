package com.paytech.gateway.guard.policy;

import java.util.Objects;

/** 一个 Guard Profile 内的不可变并发规则。 */
public record ConcurrencyRule(GuardDimension dimension, int limit) {

    public ConcurrencyRule {
        Objects.requireNonNull(dimension, "并发维度不可为空");
        if (limit <= 0) {
            throw new IllegalArgumentException("并发上限必须为正");
        }
    }
}
