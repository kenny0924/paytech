package com.paytech.foundation.messaging.test.support;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.testsupport.api.Clear;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * 每例结束清空计数器，使各用例从零断言。
 */
@TestComponent
public final class MeterRegistryCleaner implements Clear {

    private final MeterRegistry meterRegistry;

    /**
     * 创建清理器。
     *
     * @param meterRegistry 测试注册表
     */
    public MeterRegistryCleaner(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void clear() {
        meterRegistry.clear();
    }
}
