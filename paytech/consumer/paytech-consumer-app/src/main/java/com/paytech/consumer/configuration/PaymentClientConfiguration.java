package com.paytech.consumer.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;

import com.paytech.foundation.httpclient.api.HttpServiceClientFactory;
import com.paytech.foundation.httpclient.api.HttpServiceClientOptions;
import com.paytech.payment.api.card.StoredCardApi;

/** Payment 已存卡接口（列出、删除、失效）使用服务发现、负载均衡和 Foundation 公共出站边界。 */
@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = PaymentClientConfiguration.SERVICE_ID, types = StoredCardApi.class)
public class PaymentClientConfiguration {
    /** Payment 服务组名，同时是服务发现中的服务 ID。 */
    public static final String SERVICE_ID = "payment-service";

    /** 复用公共错误与 Request ID 处理，禁止自动重试和重定向；mode 非 OFF 时附加内部 Token。 */
    @Bean
    RestClientHttpServiceGroupConfigurer paymentClient(InternalTokenClientOptions internalToken) {
        return HttpServiceClientFactory.configureGroup(SERVICE_ID,
            internalToken.apply(HttpServiceClientOptions.strict().withStrictJsonResponses(8192), SERVICE_ID));
    }
}
