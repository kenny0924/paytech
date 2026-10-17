package com.paytech.foundation.xxl.core.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;

/**
 * 通用 XXL-Job Executor 的 Spring Boot 自动配置。
 *
 * @author Codex
 */
@AutoConfiguration
@ConditionalOnClass(XxlJobSpringExecutor.class)
@ConditionalOnProperty(prefix = "xxl.job.executor", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(XxlJobProperties.class)
public class XxlJobAutoConfiguration {

    public static final String EXECUTOR_BEAN = "xxlJobExecutor";

    @Bean(name = EXECUTOR_BEAN)
    @ConditionalOnMissingBean(XxlJobSpringExecutor.class)
    XxlJobSpringExecutor xxlJobExecutor(XxlJobProperties properties) {
        properties.validateEnabledConfiguration();
        XxlJobProperties.Admin admin = properties.getAdmin();
        XxlJobProperties.Executor configuration = properties.getExecutor();

        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(admin.getAddresses().trim());
        executor.setAccessToken(admin.getAccessToken().trim());
        executor.setTimeout(admin.getTimeout());
        executor.setEnabled(true);
        executor.setAppname(configuration.getAppName().trim());
        executor.setAddress(trimToNull(configuration.getAddress()));
        executor.setIp(trimToNull(configuration.getIp()));
        executor.setPort(configuration.getPort());
        executor.setLogPath(configuration.getLogPath().trim());
        executor.setLogRetentionDays(configuration.getLogRetentionDays());
        executor.setExcludedPackage(configuration.getExcludedPackages().trim());
        return executor;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
