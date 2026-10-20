package com.paytech.acquiring.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;

import com.paytech.counterparty.api.customer.CounterpartyCustomerApi;
import com.paytech.counterparty.api.enums.CounterpartyErrorCode;
import com.paytech.foundation.httpclient.api.HttpServiceClientFactory;
import com.paytech.foundation.httpclient.api.HttpServiceClientOptions;

/**
 * counterparty 内部合同（Customer 建或取、按主键读取、存卡资格）使用服务发现、负载均衡和 Foundation 公共出站边界。
 * <li>登记 counterparty 错误码，使 Customer 不存在可与依赖故障区分；禁止自动重试和重定向。</li>
 */
@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = CounterpartyClientConfiguration.SERVICE_ID, types = CounterpartyCustomerApi.class)
public class CounterpartyClientConfiguration {
    /** counterparty 服务组名，同时是服务发现中的服务 ID。 */
    public static final String SERVICE_ID = "counterparty-service";

    @Bean
    RestClientHttpServiceGroupConfigurer counterpartyClient(InternalTokenClientOptions internalToken) {
        return HttpServiceClientFactory.configureGroup(SERVICE_ID,
            internalToken.apply(HttpServiceClientOptions.strict().withStrictJsonResponses(8192), SERVICE_ID),
            CounterpartyErrorCode.values());
    }
}
