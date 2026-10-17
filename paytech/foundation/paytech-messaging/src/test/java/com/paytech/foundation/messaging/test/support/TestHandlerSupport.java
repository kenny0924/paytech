package com.paytech.foundation.messaging.test.support;

import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.test.mapper.TestMapper;

/**
 * 测试 Handler 的共同行为：先写业务行，再按观察器配置延时或失败（失败使整个消费事务回滚）。
 */
final class TestHandlerSupport {

    private TestHandlerSupport() {
    }

    static void handle(DomainEvent event, TestMapper testMapper, HandledEventProbe probe) {
        probe.enter(event.aggregateId(), event.aggregateVersion());
        try {
            testMapper.insertHandledEvent(event.eventId(), event.eventType(), event.aggregateId(),
                event.aggregateVersion());
            if (!probe.handlingDelay().isZero()) {
                Thread.sleep(probe.handlingDelay());
            }
            if (probe.shouldFail(event.aggregateId())) {
                throw new IllegalStateException("configured handler failure for " + event.aggregateId());
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("handler interrupted", interrupted);
        } finally {
            probe.exit();
        }
    }
}
