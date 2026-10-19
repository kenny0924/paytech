package com.paytech.ledger.app.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;

import com.paytech.billing.api.EconomicFactCommandApi;
import com.paytech.foundation.httpclient.api.HttpServiceClientFactory;
import com.paytech.foundation.httpclient.api.HttpServiceClientOptions;

/**
 * billing 经济事实接口使用 Foundation 公共出站边界
 * <li>ledger 调用 {@code GET /billing/v1/economic-facts/{factId}}（AccountPosted 消费与追平在事务外读事实）与
 * {@code GET /billing/v1/economic-facts/pending}（关账步骤 3）；基址由 spring.http.serviceclient.billing-service.base-url 直接配置。</li>
 */
@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = BillingClientConfiguration.SERVICE_ID, types = EconomicFactCommandApi.class)
public class BillingClientConfiguration {
    /** billing 服务组名。 */
    public static final String SERVICE_ID = "billing-service";

    /** 禁止自动重试和重定向；读事实失败由消费重投或追平返回 503，关账步骤按不可用失败关闭。 */
    @Bean
    RestClientHttpServiceGroupConfigurer billingClient() {
        return HttpServiceClientFactory.configureGroup(SERVICE_ID,
            HttpServiceClientOptions.strict().withStrictJsonResponses(8192));
    }
}
