package com.paytech.foundation.messaging.test.support;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.messaging.test.mapper.TestMapper;
import com.paytech.foundation.testsupport.api.Clear;

/**
 * 每例结束清空 messaging 自有的三张测试表。
 */
@TestComponent
public final class DatabaseCleaner implements Clear {

    private final TestMapper testMapper;

    /**
     * 创建清理器。
     *
     * @param testMapper 测试 Mapper
     */
    public DatabaseCleaner(TestMapper testMapper) {
        this.testMapper = testMapper;
    }

    @Override
    public void clear() {
        testMapper.deleteHandledEvents();
        testMapper.deleteConsumedEvents();
        testMapper.deleteConsumedAggregateVersions();
    }
}
