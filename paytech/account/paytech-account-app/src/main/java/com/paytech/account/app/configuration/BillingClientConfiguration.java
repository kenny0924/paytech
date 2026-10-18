package com.paytech.account.app.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;

import com.paytech.billing.api.EconomicFactCommandApi;
import com.paytech.foundation.httpclient.api.HttpServiceClientFactory;
import com.paytech.foundation.httpclient.api.HttpServiceClientOptions;

/**
 * billing 经济事实重放读取（余额重建，design/04 §4）使用 Foundation 公共出站边界
 * <li>account 只调用其中的 {@code GET /billing/v1/economic-facts}；基址由 spring.http.serviceclient.billing-service.base-url 直接配置。</li>
 */
@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = BillingClientConfiguration.SERVICE_ID, types = EconomicFactCommandApi.class)
public class BillingClientConfiguration {
    /** billing 服务组名。 */
    public static final String SERVICE_ID = "billing-service";

    /** 禁止自动重试和重定向；一页最多 500 条事实，响应上限放宽到 8 MiB。 */
    @Bean
    RestClientHttpServiceGroupConfigurer billingClient() {
        return HttpServiceClientFactory.configureGroup(SERVICE_ID,
            HttpServiceClientOptions.strict().withStrictJsonResponses(8 * 1024 * 1024));
    }
}
