package com.paytech.account.app.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;

import com.paytech.foundation.httpclient.api.HttpServiceClientFactory;
import com.paytech.foundation.httpclient.api.HttpServiceClientOptions;
import com.paytech.ledger.api.LedgerBookQueryApi;
import com.paytech.ledger.api.LedgerReadApi;

/**
 * ledger 账簿身份与 Owner 账户分录余额（design/04 §6、§7）使用 Foundation 公共出站边界
 * <li>Finance 后继服务不接服务发现（09 §1.4 禁 Nacos），基址由 spring.http.serviceclient.ledger-service.base-url 直接配置。</li>
 */
@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = LedgerClientConfiguration.SERVICE_ID, types = {LedgerReadApi.class, LedgerBookQueryApi.class})
public class LedgerClientConfiguration {
    /** ledger 服务组名。 */
    public static final String SERVICE_ID = "ledger-service";

    /** 禁止自动重试和重定向；失败由调用方失败关闭。 */
    @Bean
    RestClientHttpServiceGroupConfigurer ledgerClient() {
        return HttpServiceClientFactory.configureGroup(SERVICE_ID,
            HttpServiceClientOptions.strict().withStrictJsonResponses(1024 * 1024));
    }
}
