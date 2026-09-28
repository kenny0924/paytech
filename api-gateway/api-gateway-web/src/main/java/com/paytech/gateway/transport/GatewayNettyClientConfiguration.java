package com.paytech.gateway.transport;

import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 仅锁定 Spring Cloud Gateway BFF transport 的 redirect/retry 行为。 */
@Configuration(proxyBeanMethods = false)
public class GatewayNettyClientConfiguration {
    @Bean
    HttpClientCustomizer operationsBffHttpClientCustomizer() {
        return client -> client.followRedirect(false).disableRetry(true);
    }
}
