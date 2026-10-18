package com.paytech.acquiring.test.checkout;

import java.time.*;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Primary;

import com.paytech.foundation.testsupport.api.Clear;

/** Checkout HTTP 测试使用的可重置、可推进 UTC Clock。 */
@Primary
@TestComponent
public final class AdjustableUtcClock extends Clock implements Clear {

    private static final Instant DEFAULT_INSTANT = Instant.parse("2026-09-08T00:00:00Z");

    private final AtomicReference<Instant> current;
    private final ZoneId zone;

    public AdjustableUtcClock() {
        this(new AtomicReference<>(DEFAULT_INSTANT), ZoneOffset.UTC);
    }

    private AdjustableUtcClock(AtomicReference<Instant> current, ZoneId zone) {
        this.current = Objects.requireNonNull(current, "current");
        this.zone = Objects.requireNonNull(zone, "zone");
    }

    public void reset() {
        current.set(DEFAULT_INSTANT);
    }

    /** 公共 AfterEach 恢复固定时间，兼容旧测试仍显式使用 reset。 */
    @Override
    public void clear() {
        reset();
    }

    public Instant advance(Duration duration) {
        Duration increment = Objects.requireNonNull(duration, "duration");
        if (increment.isNegative() || increment.isZero()) {
            throw new IllegalArgumentException("Clock 推进时长必须为正数");
        }
        return current.updateAndGet(value -> value.plus(increment));
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId requestedZone) {
        ZoneId required = Objects.requireNonNull(requestedZone, "requestedZone");
        return zone.equals(required) ? this : new AdjustableUtcClock(current, required);
    }

    @Override
    public Instant instant() {
        return current.get();
    }
}
