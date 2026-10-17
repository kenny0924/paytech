package com.paytech.foundation.id;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Thread-safe Snowflake generator using a 41/5/5/12-bit layout.
 *
 * @author Codex
 */
public final class SnowflakeIdGenerator implements IdGenerator {

    public static final Instant DEFAULT_EPOCH = Instant.parse("2025-01-01T00:00:00Z");
    public static final Duration DEFAULT_ROLLBACK_TOLERANCE = Duration.ofMillis(5);

    private static final long REGION_BITS = 5L;
    private static final long WORKER_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;
    private static final long MAX_REGION_ID = bitMask(REGION_BITS);
    private static final long MAX_WORKER_ID = bitMask(WORKER_BITS);
    private static final long MAX_SEQUENCE = bitMask(SEQUENCE_BITS);
    private static final long MAX_TIMESTAMP_DELTA = bitMask(41L);
    private static final long WORKER_SHIFT = SEQUENCE_BITS;
    private static final long REGION_SHIFT = SEQUENCE_BITS + WORKER_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_BITS + REGION_BITS;

    private final long regionId;
    private final long workerId;
    private final Clock clock;
    private final long epochMillis;
    private final long rollbackToleranceMillis;

    private long lastTimestamp = -1L;
    private long sequence;

    public SnowflakeIdGenerator(long regionId, long workerId) {
        this(regionId, workerId, Clock.systemUTC(), DEFAULT_EPOCH, DEFAULT_ROLLBACK_TOLERANCE);
    }

    public SnowflakeIdGenerator(long regionId, long workerId, Clock clock, Instant epoch, Duration rollbackTolerance) {
        this.regionId = requireNodeId("regionId", regionId, MAX_REGION_ID);
        this.workerId = requireNodeId("workerId", workerId, MAX_WORKER_ID);
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.epochMillis = Objects.requireNonNull(epoch, "epoch must not be null").toEpochMilli();
        this.rollbackToleranceMillis = requireRollbackTolerance(rollbackTolerance);
    }

    private static long requireNodeId(String name, long value, long maximum) {
        if (value < 0L || value > maximum) {
            throw new IllegalArgumentException(name + " must be between 0 and " + maximum + ": " + value);
        }
        return value;
    }

    private static long requireRollbackTolerance(Duration rollbackTolerance) {
        Duration requiredTolerance = Objects.requireNonNull(rollbackTolerance, "rollbackTolerance must not be null");
        if (requiredTolerance.isNegative()) {
            throw new IllegalArgumentException("rollbackTolerance must not be negative");
        }
        return requiredTolerance.toMillis();
    }

    private static long bitMask(long bits) {
        return ~(-1L << bits);
    }

    @Override
    public synchronized long nextId() {
        long currentTimestamp = clock.millis();
        currentTimestamp = normalizeClockRollback(currentTimestamp);

        if (currentTimestamp < epochMillis) {
            throw new IdGenerationException(
                "当前时间早于Snowflake纪元: current=" + currentTimestamp + ", epoch=" + epochMillis);
        }

        if (currentTimestamp == lastTimestamp) {
            sequence = (sequence + 1L) & MAX_SEQUENCE;
            if (sequence == 0L) {
                currentTimestamp = requireNextMillisecond();
            }
        } else {
            sequence = 0L;
        }

        long timestampDelta = currentTimestamp - epochMillis;
        if (timestampDelta > MAX_TIMESTAMP_DELTA) {
            throw new IdGenerationException("Snowflake时间位已耗尽: timestampDelta=" + timestampDelta);
        }

        lastTimestamp = currentTimestamp;
        return (timestampDelta << TIMESTAMP_SHIFT) | (regionId << REGION_SHIFT) | (workerId << WORKER_SHIFT) | sequence;
    }

    private long normalizeClockRollback(long currentTimestamp) {
        if (lastTimestamp < 0L || currentTimestamp >= lastTimestamp) {
            return currentTimestamp;
        }

        long rollbackMillis = lastTimestamp - currentTimestamp;
        if (rollbackMillis > rollbackToleranceMillis) {
            throw new IdGenerationException(
                "检测到超出容忍范围的时钟回拨: rollbackMillis=" + rollbackMillis + ", toleranceMillis=" + rollbackToleranceMillis);
        }
        return lastTimestamp;
    }

    private long requireNextMillisecond() {
        long nextTimestamp = clock.millis();
        if (nextTimestamp <= lastTimestamp) {
            throw new IdGenerationException("同一毫秒Snowflake序列已耗尽: timestamp=" + lastTimestamp);
        }
        return nextTimestamp;
    }
}
