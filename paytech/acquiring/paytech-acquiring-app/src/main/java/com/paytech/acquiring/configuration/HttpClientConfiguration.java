package com.paytech.commerce.configuration;

import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.springframework.boot.http.client.HttpComponentsClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.autoconfigure.ClientHttpRequestFactoryBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 应用级 HTTP 传输策略；自动重试不能绕过业务命令的幂等与恢复规则。 */
@Configuration(proxyBeanMethods = false)
public class HttpClientConfiguration {
    @Bean
    ClientHttpRequestFactoryBuilderCustomizer<HttpComponentsClientHttpRequestFactoryBuilder> disableHttpRetries() {
        return builder -> builder.withHttpClientCustomizer(HttpClientBuilder::disableAutomaticRetries);
    }
}
