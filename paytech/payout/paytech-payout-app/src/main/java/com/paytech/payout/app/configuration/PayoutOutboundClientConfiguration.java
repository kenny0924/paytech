package com.paytech.payout.app.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;

import com.paytech.account.api.BalanceQueryApi;
import com.paytech.foundation.httpclient.api.HttpServiceClientFactory;
import com.paytech.foundation.httpclient.api.HttpServiceClientOptions;
import com.paytech.platform.support.api.notification.NotificationCommandApi;

/**
 * account 余额列表（paytech-account-api BalanceQueryApi）与 Platform 通知（paytech-platform-support-api NotificationCommandApi）的出站代理：服务发现、负载均衡与 Foundation 公共出站边界
 * <li>基址 spring.http.serviceclient.{account-service, platform-support-service}.base-url 为 lb://{服务名}。</li>
 * <li>余额查询在余额解释请求中、通知在补偿任务中，都在本地事务之外；对方不可用按 503 失败，不自动重试（通知由补偿任务退避重试）。</li>
 */
@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = PayoutOutboundClientConfiguration.ACCOUNT_SERVICE, types = BalanceQueryApi.class)
@ImportHttpServices(group = PayoutOutboundClientConfiguration.PLATFORM_SERVICE, types = NotificationCommandApi.class)
public class PayoutOutboundClientConfiguration {
    /** account 服务组名，同时是服务发现中的服务 ID。 */
    public static final String ACCOUNT_SERVICE = "account-service";

    /** Platform Support 服务组名，同时是服务发现中的服务 ID。 */
    public static final String PLATFORM_SERVICE = "platform-support-service";

    /** account 余额列表：禁止自动重试和重定向。 */
    @Bean
    RestClientHttpServiceGroupConfigurer accountServiceClient() {
        return HttpServiceClientFactory.configureGroup(ACCOUNT_SERVICE, HttpServiceClientOptions.strict());
    }

    /** Platform 通知：禁止自动重试和重定向；409（已受理）为并发冲突类别，由通知任务视为成功。 */
    @Bean
    RestClientHttpServiceGroupConfigurer platformServiceClient() {
        return HttpServiceClientFactory.configureGroup(PLATFORM_SERVICE, HttpServiceClientOptions.strict());
    }
}
