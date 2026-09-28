package com.paytech.gateway.test.redis;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import org.springframework.test.context.junit.jupiter.SpringExtension;

/** 为每个共享 Redis 测试类执行独立的版本、鉴权和 Sentinel 前置门禁。 */
public final class SharedRedisExtension implements BeforeAllCallback, AfterAllCallback {
    private static final ExtensionContext.Namespace STORE_NAMESPACE =
        ExtensionContext.Namespace.create(SharedRedisExtension.class);

    @Override
    public void beforeAll(ExtensionContext context) {
        var applicationContext = SpringExtension.getApplicationContext(context);
        applicationContext.getBean(RepositoryRedisEnvironment.class).assertReady();
        RedisTestCleaner cleaner = applicationContext.getBean(RedisTestCleaner.class);
        context.getStore(STORE_NAMESPACE).computeIfAbsent(SuiteResource.class,
            key -> new SuiteResource(cleaner), SuiteResource.class);
        cleaner.assertSentinelPresent();
    }

    @Override
    public void afterAll(ExtensionContext context) {
        // Context 启动/鉴权失败时没有 Sentinel，不能用二次清理异常覆盖根因。
        SuiteResource resource = context.getStore(STORE_NAMESPACE).get(SuiteResource.class, SuiteResource.class);
        if (resource != null) {
            resource.cleaner.assertSentinelPresent();
        }
    }

    private static final class SuiteResource implements AutoCloseable {
        private final RedisTestCleaner cleaner;

        private SuiteResource(RedisTestCleaner cleaner) {
            this.cleaner = cleaner;
            cleaner.cleanGuardKeys();
            cleaner.createSentinel();
        }

        @Override
        public void close() {
            cleaner.cleanGuardKeys();
            cleaner.assertSentinelPresent();
            cleaner.deleteSentinelExact();
        }
    }
}
