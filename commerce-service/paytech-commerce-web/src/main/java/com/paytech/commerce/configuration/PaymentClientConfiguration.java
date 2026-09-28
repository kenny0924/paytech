package com.paytech.commerce.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;

import com.paytech.commerce.core.configuration.CommercePaymentProperties;
import com.paytech.foundation.httpclient.api.HttpServiceClientFactory;
import com.paytech.foundation.httpclient.api.HttpServiceClientOptions;
import com.paytech.payment.api.execution.PaymentIntentApi;
import com.paytech.payment.api.instrument.PaymentInstrumentApi;

/** Payment 私有接口使用服务发现、负载均衡和 Foundation 公共出站边界。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CommercePaymentProperties.class)
@ImportHttpServices(group = "payment-orchestration-service", types = {PaymentIntentApi.class, PaymentInstrumentApi.class})
public class PaymentClientConfiguration {
    /** 复用公共错误与 Request ID 处理，禁止命令自动重试和重定向。 */
    @Bean
    RestClientHttpServiceGroupConfigurer paymentClient() {
        return HttpServiceClientFactory.configureGroup("payment-orchestration-service",
            HttpServiceClientOptions.strict().withStrictJsonResponses(8192));
    }
}
