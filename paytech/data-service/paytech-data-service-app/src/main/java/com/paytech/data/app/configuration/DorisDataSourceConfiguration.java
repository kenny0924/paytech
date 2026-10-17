package com.paytech.data.app.configuration;

import com.paytech.data.app.health.DorisHealthIndicator;
import com.paytech.foundation.common.error.ErrorException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Doris 只读 DataSource 与连通性健康检查；{@code data-service.doris.enabled=false} 时整组不创建。
 *
 * @author Claude
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "data-service.doris", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DorisDataSourceConfiguration {

    /**
     * 创建 Doris 连接池；启动时不预建连接，Doris 暂不可用只影响健康状态。
     *
     * @param properties Doris 配置
     * @return 连接池
     */
    @Bean(destroyMethod = "close")
    public HikariDataSource dorisDataSource(DorisProperties properties) {
        requireText(properties.getUrl(), "data-service.doris.url");
        requireText(properties.getUsername(), "data-service.doris.username");
        requireText(properties.getPassword(), "data-service.doris.password");
        if (properties.getConnectTimeout() == null) {
            throw new ErrorException("Doris 配置缺失: data-service.doris.connect-timeout");
        }
        HikariConfig config = new HikariConfig();
        config.setPoolName("doris");
        config.setJdbcUrl(properties.getUrl());
        config.setUsername(properties.getUsername());
        config.setPassword(properties.getPassword());
        config.setReadOnly(true);
        config.setMaximumPoolSize(4);
        config.setMinimumIdle(0);
        config.setInitializationFailTimeout(-1);
        config.setConnectionTimeout(properties.getConnectTimeout().toMillis());
        config.setValidationTimeout(properties.getConnectTimeout().toMillis());
        return new HikariDataSource(config);
    }

    /**
     * 注册 Doris 健康指示器（组件名 {@code doris}）。
     *
     * @param dorisDataSource Doris 连接池
     * @param properties Doris 配置
     * @return 健康指示器
     */
    @Bean
    public DorisHealthIndicator dorisHealthIndicator(DataSource dorisDataSource, DorisProperties properties) {
        return new DorisHealthIndicator(dorisDataSource, properties.getConnectTimeout());
    }

    private static void requireText(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new ErrorException("Doris 配置缺失: " + key);
        }
    }
}
