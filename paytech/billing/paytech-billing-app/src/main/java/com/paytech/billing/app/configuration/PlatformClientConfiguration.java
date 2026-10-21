package com.paytech.billing.app.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;

import com.paytech.foundation.httpclient.api.HttpServiceClientFactory;
import com.paytech.foundation.httpclient.api.HttpServiceClientOptions;
import com.paytech.platform.support.api.referencedata.CurrencySpecQueryApi;

/**
 * platform-support 币种规则查询接口使用 Foundation 公共出站边界
 * <li>billing 只注册 {@link CurrencySpecQueryApi}：币种快照刷新在事务外调用 {@code GET /platform/v1/currency-specs}；基址由
 * spring.http.serviceclient.platform-support-service.base-url 直接配置（含 Platform Context Path）。</li>
 */
@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = PlatformClientConfiguration.SERVICE_ID, types = CurrencySpecQueryApi.class)
public class PlatformClientConfiguration {
    /** platform-support 服务组名。 */
    public static final String SERVICE_ID = "platform-support-service";

    /** 禁止自动重试和重定向；读取失败保留上一份快照，由下一个检查周期重试。 */
    @Bean
    RestClientHttpServiceGroupConfigurer platformSupportClient() {
        return HttpServiceClientFactory.configureGroup(SERVICE_ID,
            HttpServiceClientOptions.strict().withStrictJsonResponses(65536));
    }
}
