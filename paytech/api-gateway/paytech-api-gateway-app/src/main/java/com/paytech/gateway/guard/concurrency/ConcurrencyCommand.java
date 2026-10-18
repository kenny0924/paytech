package com.paytech.gateway.guard.concurrency;

import java.util.Objects;

/** 一次原子并发 Lease 获取中的一个维度命令。 */
public record ConcurrencyCommand(ConcurrencyKeys keys, int limit, long leaseDurationMillis, long stateTtlMillis) {

    static final long MAX_SAFE_INTEGER = 9_007_199_254_740_991L;

    public ConcurrencyCommand {
        Objects.requireNonNull(keys, "并发 Redis Key 对不可为空");
        if (limit <= 0) {
            throw new IllegalArgumentException("并发上限必须为正整数");
        }
        requirePositiveSafe(leaseDurationMillis, "并发 Lease 时长");
        requirePositiveSafe(stateTtlMillis, "并发状态 TTL");
        if (stateTtlMillis != safeDouble(leaseDurationMillis)) {
            throw new IllegalArgumentException("并发状态 TTL 必须为 Lease 时长的两倍");
        }
    }

    /** 兼容调用方的 Base Key 构造入口，内部立即收敛为显式 Key 对。 */
    public ConcurrencyCommand(String baseKey, int limit, long leaseDurationMillis, long stateTtlMillis) {
        this(ConcurrencyKeys.fromBaseKey(baseKey), limit, leaseDurationMillis, stateTtlMillis);
    }

    private static long safeDouble(long value) {
        if (value > MAX_SAFE_INTEGER / 2) {
            throw new IllegalArgumentException("并发 Lease 时长超出精确整数范围");
        }
        return value * 2;
    }

    private static void requirePositiveSafe(long value, String name) {
        if (value <= 0 || value > MAX_SAFE_INTEGER) {
            throw new IllegalArgumentException(name + "必须是 IEEE-754 精确正整数");
        }
    }

    public String key() {
        return keys.leaseKey();
    }

    public long keyTtlMillis() {
        return stateTtlMillis;
    }
}
