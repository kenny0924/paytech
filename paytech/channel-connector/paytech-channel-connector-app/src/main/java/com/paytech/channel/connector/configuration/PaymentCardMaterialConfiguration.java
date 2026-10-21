package com.paytech.channel.connector.configuration;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerRequestFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.paytech.card.api.CardApiConstants;
import com.paytech.card.api.material.ChannelCardMaterialApi;
import com.paytech.channel.connector.protocol.core.configuration.ExternalApiExecutionProperties;
import com.paytech.foundation.httpclient.api.HttpServiceClientFactory;
import com.paytech.foundation.httpclient.api.HttpServiceClientOptions;

/**
 * 卡直付执行读卡客户端（PayerMax 卡直付对接技术方案第 2、3.1 节；2026-10-20 随执行引擎自 Platform 迁入）：以
 * channel-connector-service 身份凭一次性票据经私网服务发现调用 card（含其 context path），不跟随重定向。
 *
 * <p>
 * 响应含卡原值：Foundation 出站客户端不记录报文，调用方只在内存中使用。读卡超时 2 秒，受流程总预算约束。
 */
@Configuration(proxyBeanMethods = false)
public class PaymentCardMaterialConfiguration {

    private static final String CARD_SERVICE = CardApiConstants.SERVICE_ID;

    /**
     * card 读卡接口客户端；internal-auth.mode 非 OFF 时附加访问 card 的内部 Token
     *
     * @param discovery 服务发现负载均衡客户端，缺失时请求失败
     * @param properties 协议执行配置，提供 card 的 context path
     * @param internalToken 出站内部 Token 选项
     * @return 读卡接口代理
     */
    @Bean
    ChannelCardMaterialApi channelCardMaterialApi(ObjectProvider<LoadBalancerClient> discovery,
        ExternalApiExecutionProperties properties, InternalTokenClientOptions internalToken) {
        String baseUrl = "http://" + CARD_SERVICE + properties.getCardContextPath();
        var builder = RestClient.builder().baseUrl(baseUrl).requestInterceptor((request, body, execution) -> {
            var balancer = discovery.getIfAvailable();
            var instance = balancer == null ? null : balancer.choose(CARD_SERVICE);
            if (instance == null) {
                throw new ResourceAccessException("Card discovery unavailable");
            }
            return balancer.execute(CARD_SERVICE, instance,
                new LoadBalancerRequestFactory(balancer).createRequest(request, body, execution));
        });
        var transport = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1))
            .followRedirects(HttpClient.Redirect.NEVER).build());
        transport.setReadTimeout(Duration.ofSeconds(2));
        return HttpServiceClientFactory
            .create(builder.requestFactory(transport).build(),
                internalToken.apply(HttpServiceClientOptions.strict().withStrictJsonResponses(8192), CARD_SERVICE))
            .createClient(ChannelCardMaterialApi.class);
    }
}
