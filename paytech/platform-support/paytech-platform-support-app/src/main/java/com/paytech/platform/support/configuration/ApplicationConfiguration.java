package com.paytech.platform.support.configuration;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 应用级基础依赖，不依赖具体业务能力的启用开关。 */
@Configuration(proxyBeanMethods = false)
public class ApplicationConfiguration {

    /** 为业务组件提供统一的 UTC 时钟。 */
    @Bean
    Clock applicationClock() {
        return Clock.systemUTC();
    }
}
