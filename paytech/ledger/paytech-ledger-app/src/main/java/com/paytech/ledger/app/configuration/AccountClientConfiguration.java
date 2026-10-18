package com.paytech.ledger.app.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;

import com.paytech.account.api.BalanceBookApi;
import com.paytech.foundation.httpclient.api.HttpServiceClientFactory;
import com.paytech.foundation.httpclient.api.HttpServiceClientOptions;

/**
 * account 账簿余额一致性接口（关账步骤 2、3，design/04 §6）使用 Foundation 公共出站边界
 * <li>Finance 后继服务不接服务发现（09 §1.4 禁 Nacos），基址由 spring.http.serviceclient.account-service.base-url 直接配置。</li>
 */
@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = AccountClientConfiguration.SERVICE_ID, types = BalanceBookApi.class)
public class AccountClientConfiguration {
    /** account 服务组名。 */
    public static final String SERVICE_ID = "account-service";

    /** 禁止自动重试和重定向；失败由关账步骤按不可用失败关闭。 */
    @Bean
    RestClientHttpServiceGroupConfigurer accountClient() {
        return HttpServiceClientFactory.configureGroup(SERVICE_ID,
            HttpServiceClientOptions.strict().withStrictJsonResponses(8192));
    }
}
