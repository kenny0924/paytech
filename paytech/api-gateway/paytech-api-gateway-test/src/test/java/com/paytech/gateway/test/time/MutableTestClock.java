package com.paytech.gateway.test.time;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** 可由测试显式推进、不会读取宿主机时间的 Clock。 */
public final class MutableTestClock extends Clock {
    private final AtomicReference<Instant> current;
    private final ZoneId zone;

    public MutableTestClock(Instant initialInstant, ZoneId zone) {
        this.current = new AtomicReference<>(Objects.requireNonNull(initialInstant, "initialInstant must not be null"));
        this.zone = Objects.requireNonNull(zone, "zone must not be null");
    }

    public void reset(Instant instant) {
        current.set(Objects.requireNonNull(instant, "instant must not be null"));
    }

    public void advance(Duration duration) {
        if (duration.isNegative()) {
            throw new IllegalArgumentException("测试时间推进量不能为负数");
        }
        current.updateAndGet(instant -> instant.plus(duration));
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId requestedZone) {
        return new MutableTestClock(instant(), requestedZone);
    }

    @Override
    public Instant instant() {
        return current.get();
    }
}
