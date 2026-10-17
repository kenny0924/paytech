package com.paytech.platform.support.configuration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import com.paytech.foundation.redis.core.autoconfigure.RedisResourceLockProperties;

/** 显式打开来源写入口时验证已有 IAM、ID、审计和 Redis 基础能力。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "tenant.projection.enabled", havingValue = "true")
public class TenantProjectionConfiguration {
    /** 当前应用仍沿用认证模块提供的共用基础能力，不重复创建 Snowflake Worker。 */
    public TenantProjectionConfiguration(Environment environment, RedisResourceLockProperties lockProperties) {
        if (!environment.getProperty("client.authentication.enabled", Boolean.class, false)
            && !environment.getProperty("operations.authentication.enabled", Boolean.class, false)) {
            throw new IllegalStateException("租户来源投影需要先启用当前 Platform IAM 与审计基础能力");
        }
        if (!environment.getProperty("redis.coordination.enabled", Boolean.class, false)) {
            throw new IllegalStateException("租户来源投影必须配置 redis.coordination.enabled");
        }
        // 模板迁移后即使没有注解锁消费者，仍在启动期校验原服务级有限 Wait/Lease。
        lockProperties.validateRequiredConfiguration();
    }
}
