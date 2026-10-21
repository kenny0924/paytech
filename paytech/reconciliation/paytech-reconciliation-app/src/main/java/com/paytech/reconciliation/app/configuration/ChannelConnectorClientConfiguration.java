package com.paytech.reconciliation.app.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;

import com.paytech.channel.connector.api.channel.ChannelMidApi;
import com.paytech.foundation.httpclient.api.HttpServiceClientFactory;
import com.paytech.foundation.httpclient.api.HttpServiceClientOptions;

/**
 * channel-connector MID 查询接口使用服务发现、负载均衡和 Foundation 公共出站边界
 * <li>只注册 {@link ChannelMidApi}，只调用列表与详情两个查询（挂 MID 前核对渠道、经营主体、状态与能力组币种；未归属 MID）；
 * MID 写命令 reconciliation 不使用。基址 spring.http.serviceclient.channel-connector-service.base-url 为 lb://channel-connector-service。</li>
 * <li>调用都在本地事务之前（事务公理）；对方不可用按 503 失败关闭，不自动重试。</li>
 */
@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = ChannelConnectorClientConfiguration.SERVICE_ID, types = ChannelMidApi.class)
public class ChannelConnectorClientConfiguration {
    /** channel-connector 服务组名，同时是服务发现中的服务 ID。 */
    public static final String SERVICE_ID = "channel-connector-service";

    /** 禁止自动重试和重定向；MID 详情含能力组，响应上限 64 KiB。 */
    @Bean
    RestClientHttpServiceGroupConfigurer channelConnectorClient() {
        return HttpServiceClientFactory.configureGroup(SERVICE_ID,
            HttpServiceClientOptions.strict().withStrictJsonResponses(65536));
    }
}
