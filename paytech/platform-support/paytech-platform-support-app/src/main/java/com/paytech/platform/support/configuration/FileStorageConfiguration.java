package com.paytech.platform.support.configuration;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.ConfigurationPropertyState;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 文件存储的启动约束：存储实例、桶与路由只在运营「存储管理」中维护
 * <li>配置文件仍有旧的 files.onboarding 存储键（含环境变量形式）时启动失败，提示改到存储管理。</li>
 * <li>files.enabled=true 时要求 Redis 资源协调与本地补偿执行器，并启用存储快照的定时刷新。</li>
 */
@Configuration(proxyBeanMethods = false)
public class FileStorageConfiguration {

    /** 已删除的存储配置键；任一存在即启动失败。 */
    static final List<String> REMOVED_KEYS = List.of("files.onboarding.storage-provider",
        "files.onboarding.storage-container-ref", "files.onboarding.scan-policy-code", "files.onboarding.upload-seconds",
        "files.onboarding.minio", "files.onboarding.s3");

    /** 启动时拒绝旧存储键。 */
    public FileStorageConfiguration(Environment environment) {
        for (String key : REMOVED_KEYS) {
            if (present(environment, key)) {
                throw new IllegalStateException("存储配置已改为运营存储管理，请删除配置项 " + key
                    + "（含对应环境变量），并在运营后台「存储管理」登记存储实例、存储桶与写入路由");
            }
        }
    }

    /** 按 Spring Boot 宽松绑定判断键本身或其下级是否存在，覆盖 YAML、系统属性与环境变量。 */
    static boolean present(Environment environment, String key) {
        ConfigurationPropertyName name = ConfigurationPropertyName.of(key);
        for (ConfigurationPropertySource source : ConfigurationPropertySources.get(environment)) {
            if (source.getConfigurationProperty(name) != null
                || source.containsDescendantOf(name) == ConfigurationPropertyState.PRESENT) {
                return true;
            }
        }
        return false;
    }

    /** 文件能力启用时的依赖检查与快照定时刷新。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "files.enabled", havingValue = "true")
    @EnableScheduling
    static class Enabled {

        /** 内部文件与扫描需要 Redis 资源协调与本地补偿执行器。 */
        Enabled(Environment environment) {
            if (!environment.getProperty("redis.coordination.enabled", Boolean.class, false)
                || !environment.getProperty("compensation.enabled", Boolean.class, true)) {
                throw new IllegalStateException("files.enabled 需要 Redis 资源协调与本地补偿执行器");
            }
        }
    }
}
