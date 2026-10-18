package com.paytech.foundation.internalauth.test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * 可推进的测试时钟，用于有效期、缓存与刷新间隔场景。
 */
public final class MutableClock extends Clock {

    private volatile Instant now;

    /**
     * 创建时钟。
     *
     * @param start 起始时间
     */
    public MutableClock(Instant start) {
        this.now = start;
    }

    /**
     * 推进时间。
     *
     * @param duration 时长
     */
    public void advance(Duration duration) {
        now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return now;
    }
}
