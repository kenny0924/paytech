package com.paytech.foundation.compensation.test.support;

import java.time.Duration;
import java.util.Objects;

import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationEventHandler;
import com.paytech.foundation.compensation.api.CompensationExecutionPolicy;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;
import com.paytech.foundation.compensation.test.mapper.TestMapper;

/**
 * 从数据库加载执行上下文的测试处理器。
 *
 * @author Codex
 */
public final class DatabaseLookupCompensationHandler implements CompensationEventHandler {

    public static final String EVENT_TYPE = "ProcessBusinessSource";

    private final TestMapper testMapper;
    private final TestHandlerProbe probe;

    public DatabaseLookupCompensationHandler(TestMapper testMapper, TestHandlerProbe probe) {
        this.testMapper = Objects.requireNonNull(testMapper, "testMapper must not be null");
        this.probe = Objects.requireNonNull(probe, "probe must not be null");
    }

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    @Override
    public CompensationExecutionPolicy policy() {
        return new CompensationExecutionPolicy(Duration.ofSeconds(5), 3, Duration.ofMillis(100), Duration.ofSeconds(2),
            2.0D, 0.0D);
    }

    @Override
    public CompensationHandlerResult handle(CompensationEvent event) {
        String businessValue = testMapper.findBusinessValue(event.eventKey(), event.subEventType());
        if (businessValue == null) {
            return CompensationHandlerResult.retryableFailure("SOURCE_NOT_FOUND", "business source is not available");
        }
        testMapper.markBusinessSourceProcessed(event.eventKey(), event.subEventType());
        probe.record(event);
        return CompensationHandlerResult.success();
    }
}
