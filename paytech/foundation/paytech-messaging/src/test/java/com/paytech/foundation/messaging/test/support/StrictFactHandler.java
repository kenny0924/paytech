package com.paytech.foundation.messaging.test.support;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.api.DomainEventConsumer;
import com.paytech.foundation.messaging.api.DomainEventHandler;
import com.paytech.foundation.messaging.test.mapper.TestMapper;

/**
 * 严格有序的测试 Handler：在消费事务内写一行业务数据。
 */
@TestComponent
@DomainEventHandler(eventType = TestRun.STRICT_EVENT, strictOrder = true)
public final class StrictFactHandler implements DomainEventConsumer {

    private final TestMapper testMapper;
    private final HandledEventProbe probe;

    /**
     * 创建 Handler。
     *
     * @param testMapper 测试 Mapper
     * @param probe 观察器
     */
    public StrictFactHandler(TestMapper testMapper, HandledEventProbe probe) {
        this.testMapper = testMapper;
        this.probe = probe;
    }

    @Override
    public void consume(DomainEvent event) {
        TestHandlerSupport.handle(event, testMapper, probe);
    }
}
