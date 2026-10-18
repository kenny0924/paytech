package com.paytech.foundation.id.auto;

import static org.junit.jupiter.api.Assertions.*;

import java.time.*;

import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.id.IdErrorCode;
import com.paytech.foundation.id.IdGenerationException;
import com.paytech.foundation.id.SnowflakeIdGenerator;

class SnowflakeIdGeneratorTest {

    private static final Instant EPOCH = Instant.parse("2025-01-01T00:00:00Z");

    @Test
    void generatesUniqueIncreasingIdsWithinSameMillisecond() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-28T10:00:00Z"));
        SnowflakeIdGenerator generator = generator(0, 0, clock, Duration.ofMillis(5));

        long first = generator.nextId();
        long second = generator.nextId();

        assertNotEquals(first, second);
        assertTrue(second > first);
    }

    @Test
    void rejectsRegionOrWorkerOutsideFiveBitRange() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-28T10:00:00Z"));

        assertThrows(IllegalArgumentException.class, () -> generator(-1, 0, clock, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> generator(32, 0, clock, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> generator(0, -1, clock, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> generator(0, 32, clock, Duration.ZERO));
    }

    @Test
    void toleratesSmallClockRollbackWithoutDuplicateId() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-28T10:00:00Z"));
        SnowflakeIdGenerator generator = generator(0, 0, clock, Duration.ofMillis(5));
        long first = generator.nextId();

        clock.setInstant(clock.instant().minusMillis(2));
        long second = generator.nextId();

        assertTrue(second > first);
    }

    @Test
    void failsClosedWhenClockRollbackExceedsTolerance() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-28T10:00:00Z"));
        SnowflakeIdGenerator generator = generator(0, 0, clock, Duration.ofMillis(5));
        generator.nextId();
        clock.setInstant(clock.instant().minusMillis(6));

        IdGenerationException exception = assertThrows(IdGenerationException.class, generator::nextId);

        assertInstanceOf(ErrorException.class, exception);
        assertEquals(IdErrorCode.GENERATION_ERROR.getCode(), exception.getCode());
    }

    @Test
    void rejectsTimestampBeforeConfiguredEpoch() {
        MutableClock clock = new MutableClock(EPOCH.minusMillis(1));
        SnowflakeIdGenerator generator = generator(0, 0, clock, Duration.ZERO);

        assertThrows(IdGenerationException.class, generator::nextId);
    }

    private SnowflakeIdGenerator generator(long regionId, long workerId, Clock clock, Duration rollbackTolerance) {
        return new SnowflakeIdGenerator(regionId, workerId, clock, EPOCH, rollbackTolerance);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new IllegalArgumentException("Only UTC is supported by this test clock");
            }
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void setInstant(Instant instant) {
            this.instant = instant;
        }
    }
}
