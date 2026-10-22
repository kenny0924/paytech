package com.paytech.platform.support.configuration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.paytech.platform.support.dataquery.core.configuration.DataQueryCallerProperties;

/** 数据查询的应用装配：执行接口调用方白名单（构造器绑定）与连接池定时刷新。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DataQueryCallerProperties.class)
public class DataQueryConfiguration {

    /** data-query.datasource.refresh-enabled 为 true（默认）时启用调度，驱动连接池刷新任务。 */
    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @ConditionalOnProperty(name = "data-query.datasource.refresh-enabled", havingValue = "true", matchIfMissing = true)
    static class DataSourceRefreshScheduling {
    }
}
