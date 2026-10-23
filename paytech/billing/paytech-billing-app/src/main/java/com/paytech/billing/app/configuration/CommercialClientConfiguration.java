package com.paytech.billing.app.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;

import com.paytech.commercial.api.price.CommercialPriceApi;
import com.paytech.commercial.api.enums.CommercialPriceErrorCode;
import com.paytech.foundation.httpclient.api.HttpServiceClientFactory;
import com.paytech.foundation.httpclient.api.HttpServiceClientOptions;

/**
 * commercial 价格命中接口使用 Foundation 公共出站边界
 * <li>billing 只注册 {@link CommercialPriceApi}，只调用 {@code POST /commercial-prices/v1/resolutions}（按价格 ID 回读不使用）；基址由
 * spring.http.serviceclient.commercial-service.base-url 直接配置。</li>
 * <li>注册价格命中的四个合同错误码（事实不完整、无命中、事实不合法、歧义），使 409 / 422 解码为明确失败。</li>
 */
@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = CommercialClientConfiguration.SERVICE_ID, types = CommercialPriceApi.class)
public class CommercialClientConfiguration {
    /** 服务组名。 */
    public static final String SERVICE_ID = "commercial-service";

    /** 禁止自动重试和重定向；登记计费事实前事务外读取，失败由消息退避重投，不在客户端重试。 */
    @Bean
    RestClientHttpServiceGroupConfigurer commercialClient() {
        return HttpServiceClientFactory.configureGroup(SERVICE_ID,
            HttpServiceClientOptions.strict().withStrictJsonResponses(65536), CommercialPriceErrorCode.values());
    }
}
