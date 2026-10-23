package com.paytech.billing.app.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;

import com.paytech.channel.connector.api.cost.ChannelCostResolutionApi;
import com.paytech.channel.connector.api.enums.ChannelCostErrorCode;
import com.paytech.foundation.httpclient.api.HttpServiceClientFactory;
import com.paytech.foundation.httpclient.api.HttpServiceClientOptions;

/**
 * channel-connector 渠道成本命中接口使用 Foundation 公共出站边界
 * <li>billing 只注册 {@link ChannelCostResolutionApi}：登记自动 PAYMENT 事实时事务外调用 {@code POST /channel-connector/v1/channel-costs/resolutions}；
 * 基址由 spring.http.serviceclient.channel-connector-service.base-url 直接配置。</li>
 * <li>注册未命中与命中不唯一两个合同错误码，使 400 解码为明确失败。</li>
 */
@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = ChannelConnectorClientConfiguration.SERVICE_ID, types = ChannelCostResolutionApi.class)
public class ChannelConnectorClientConfiguration {
    /** 服务组名。 */
    public static final String SERVICE_ID = "channel-connector-service";

    /** 禁止自动重试和重定向；登记计费事实前事务外读取，失败由消息退避重投，不在客户端重试。 */
    @Bean
    RestClientHttpServiceGroupConfigurer channelConnectorClient() {
        return HttpServiceClientFactory.configureGroup(SERVICE_ID,
            HttpServiceClientOptions.strict().withStrictJsonResponses(65536), ChannelCostErrorCode.values());
    }
}
