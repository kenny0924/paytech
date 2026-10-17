package com.paytech.channel.connector.configuration;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerRequestFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.paytech.foundation.httpclient.api.HttpServiceClientFactory;
import com.paytech.foundation.httpclient.api.HttpServiceClientOptions;
import com.paytech.platform.support.api.enums.StandardApiExecutionErrorCode;
import com.paytech.platform.support.api.standardapi.StandardApiCommandApi;

/**
 * 出站 HTTP 客户端：经私网服务发现调用 Platform 标准 API，不跟随重定向、不自动重试。
 * <li>不记录报文；Platform 执行可能已发出渠道请求，超时按结果未知处理。</li>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ChannelConnectorOutboundProperties.class)
public class ChannelConnectorOutboundConfiguration {
    private static final String PLATFORM_SERVICE = "platform-support-service";

    /** Platform 标准 API 内部执行客户端；internal-auth 非 OFF 时附加内部 Token。 */
    @Bean
    StandardApiCommandApi standardApiCommandApi(RestClient.Builder builder, ObjectProvider<LoadBalancerClient> discovery,
        ChannelConnectorOutboundProperties properties, InternalTokenClientOptions internalToken) {
        RestClient client = client(builder, discovery, PLATFORM_SERVICE, properties.getPlatformContextPath(),
            properties.getPlatformReadTimeout());
        HttpServiceClientOptions options =
            internalToken.apply(HttpServiceClientOptions.strict().withStrictJsonResponses(65536), PLATFORM_SERVICE);
        return HttpServiceClientFactory.create(client, options, StandardApiExecutionErrorCode.values())
            .createClient(StandardApiCommandApi.class);
    }

    /** 基于应用的 RestClient.Builder（保留公共观测定制）：服务发现选择实例，地址带对方应用 context path；连接 2 秒。 */
    static RestClient client(RestClient.Builder builder, ObjectProvider<LoadBalancerClient> discovery, String serviceId,
        String contextPath, Duration readTimeout) {
        var transport = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER).build());
        transport.setReadTimeout(readTimeout);
        return builder.baseUrl("http://" + serviceId + contextPath)
            .requestInterceptor((request, body, execution) -> {
                var balancer = discovery.getIfAvailable();
                var instance = balancer == null ? null : balancer.choose(serviceId);
                if (instance == null) {
                    throw new ResourceAccessException(serviceId + " discovery unavailable");
                }
                return balancer.execute(serviceId, instance,
                    new LoadBalancerRequestFactory(balancer).createRequest(request, body, execution));
            }).requestFactory(transport).build();
    }
}
