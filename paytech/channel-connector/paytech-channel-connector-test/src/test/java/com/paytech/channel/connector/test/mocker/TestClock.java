package com.paytech.channel.connector.test.mocker;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Primary;

import com.paytech.foundation.testsupport.api.Clear;

/**
 * 测试装配的 UTC 应用时钟：默认跟随系统时间，与生产时钟行为一致；需要确定协议时间的用例显式固定时点，每例结束恢复
 * <li>自 Platform 迁入的签名渠道用例依赖原 Platform 测试固定时钟（2026-09-17T12:00:00.123456Z）断言请求字节与签名。</li>
 */
@Primary
@TestComponent
public class TestClock extends Clock implements Clear {
    /** 自 Platform 迁入用例沿用的固定时点。 */
    public static final Instant PLATFORM_FIXED_NOW = Instant.parse("2026-09-17T12:00:00.123456Z");

    private final AtomicReference<Instant> fixed = new AtomicReference<>();

    /**
     * 固定当前时点，直到本例结束
     *
     * @param instant 固定时点
     */
    public void fix(Instant instant) {
        fixed.set(Objects.requireNonNull(instant, "instant must not be null"));
    }

    /** 保持生产时间合同的 UTC 时区。 */
    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    /** 切换时区只用于展示，不改变时点；UTC 返回本时钟以保留后续固定与恢复。 */
    @Override
    public Clock withZone(ZoneId zone) {
        return ZoneOffset.UTC.equals(zone) ? this : Clock.fixed(instant(), zone);
    }

    /** 已固定时返回固定时点，否则返回系统 UTC 时间。 */
    @Override
    public Instant instant() {
        Instant current = fixed.get();
        return current == null ? Instant.now() : current;
    }

    /** 每例恢复跟随系统时间。 */
    @Override
    public void clear() {
        fixed.set(null);
    }
}
