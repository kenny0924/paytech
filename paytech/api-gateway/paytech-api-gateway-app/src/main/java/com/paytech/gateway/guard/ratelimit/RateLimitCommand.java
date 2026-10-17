package com.paytech.gateway.guard.ratelimit;

import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** 一次原子 Token Bucket 评估中的一个 Bucket 命令。 */
public final class RateLimitCommand {
    static final long MAX_SAFE_INTEGER = 9_007_199_254_740_991L;
    private static final int MAX_KEY_UTF8_BYTES = 1_024;
    private static final long MINIMUM_TTL_MILLIS = 60_000L;
    private final String key;
    private final long capacityMicrotokens;
    private final long refillMicrotokens;
    private final long refillPeriodMillis;
    private final long requestedMicrotokens;

    public RateLimitCommand(String key, long capacityMicrotokens, long refillMicrotokens, long refillPeriodMillis,
        long requestedMicrotokens) {
        if (key == null || key.isBlank() || key.codePoints().anyMatch(Character::isISOControl)
            || utf8Length(key) > MAX_KEY_UTF8_BYTES) {
            throw new IllegalArgumentException("限流 Redis Key 格式无效");
        }
        requirePositiveSafe(capacityMicrotokens, "Bucket 容量");
        requirePositiveSafe(refillMicrotokens, "Bucket 补充量");
        requirePositiveSafe(refillPeriodMillis, "Bucket 补充周期");
        requirePositiveSafe(requestedMicrotokens, "Bucket 请求量");
        if (requestedMicrotokens > capacityMicrotokens) {
            throw new IllegalArgumentException("Bucket 请求量不可超过容量");
        }
        deriveTtlMillis(capacityMicrotokens, refillMicrotokens, refillPeriodMillis);
        this.key = key;
        this.capacityMicrotokens = capacityMicrotokens;
        this.refillMicrotokens = refillMicrotokens;
        this.refillPeriodMillis = refillPeriodMillis;
        this.requestedMicrotokens = requestedMicrotokens;
    }

    public static long deriveTtlMillis(long capacityMicrotokens, long refillMicrotokens, long refillPeriodMillis) {
        requirePositiveSafe(capacityMicrotokens, "Bucket 容量");
        requirePositiveSafe(refillMicrotokens, "Bucket 补充量");
        requirePositiveSafe(refillPeriodMillis, "Bucket 补充周期");
        long capacityPeriod = safeMultiply(capacityMicrotokens, refillPeriodMillis, "Bucket 补满时间乘法超出精确整数范围");
        long emptyToFullMillis = ceilDivide(capacityPeriod, refillMicrotokens);
        long refillNumerator = safeMultiply(emptyToFullMillis, refillMicrotokens, "Bucket 补充计算超出精确整数范围");
        safeAdd(refillNumerator, refillPeriodMillis - 1, "Bucket 余数计算超出精确整数范围");
        long doubled = safeMultiply(emptyToFullMillis, 2, "Bucket TTL 超出精确整数范围");
        return Math.max(MINIMUM_TTL_MILLIS, doubled);
    }

    static long safeMultiply(long left, long right, String message) {
        if (left > MAX_SAFE_INTEGER / right) {
            throw new IllegalArgumentException(message);
        }
        return left * right;
    }

    static long safeAdd(long left, long right, String message) {
        if (left > MAX_SAFE_INTEGER - right) {
            throw new IllegalArgumentException(message);
        }
        return left + right;
    }

    static long ceilDivide(long numerator, long denominator) {
        long quotient = numerator / denominator;
        return numerator % denominator == 0 ? quotient : quotient + 1;
    }

    private static void requirePositiveSafe(long value, String name) {
        if (value <= 0 || value > MAX_SAFE_INTEGER) {
            throw new IllegalArgumentException(name + "必须是 IEEE-754 精确正整数");
        }
    }

    private static int utf8Length(String value) {
        try {
            return StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value)).remaining();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("限流 Redis Key 不是有效 Unicode", exception);
        }
    }

    public String key() {
        return key;
    }

    public long capacityMicrotokens() {
        return capacityMicrotokens;
    }

    public long refillMicrotokens() {
        return refillMicrotokens;
    }

    public long refillPeriodMillis() {
        return refillPeriodMillis;
    }

    public long requestedMicrotokens() {
        return requestedMicrotokens;
    }

}
