package com.paytech.ledger.app.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;

import com.paytech.account.api.AccountPostingApi;
import com.paytech.foundation.httpclient.api.HttpServiceClientFactory;
import com.paytech.foundation.httpclient.api.HttpServiceClientOptions;

/**
 * account 过账进度与批次接口（关账步骤 3 CHECK_ACCOUNT_POSTING_PROGRESS、过账流追平）使用 Foundation 公共出站边界
 * <li>Finance 后继服务不接服务发现（09 §1.4 禁 Nacos），基址由 spring.http.serviceclient.account-service.base-url 直接配置。</li>
 */
@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = AccountClientConfiguration.SERVICE_ID, types = AccountPostingApi.class)
public class AccountClientConfiguration {
    /** account 服务组名。 */
    public static final String SERVICE_ID = "account-service";

    /** 禁止自动重试和重定向；关账步骤按不可用失败关闭，追平命令返回 503。 */
    @Bean
    RestClientHttpServiceGroupConfigurer accountClient() {
        return HttpServiceClientFactory.configureGroup(SERVICE_ID,
            HttpServiceClientOptions.strict().withStrictJsonResponses(8192));
    }
}
