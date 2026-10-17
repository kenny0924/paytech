package com.paytech.payment.configuration;

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

import com.paytech.channel.connector.api.candidate.ChannelCandidateQueryApi;
import com.paytech.channel.connector.api.operation.ChannelOperationApi;
import com.paytech.foundation.httpclient.api.HttpServiceClientFactory;
import com.paytech.foundation.httpclient.api.HttpServiceClientOptions;
import com.paytech.risk.api.evaluation.RiskDecisionApi;

/**
 * Execution 出站 HTTP 客户端：经私网服务发现调用 channel-connector（候选查询与渠道操作，D06）与 Risk 统一评估，不跟随重定向、不自动重试。
 * <li>出站客户端不记录报文；渠道操作可能已发出渠道请求，超时由调用方按结果未知处理。</li>
 * <li>internal-auth.mode 非 OFF 时附加内部 Token（D26），见 {@link InternalTokenClientOptions}。</li>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PaymentOutboundProperties.class)
public class PaymentOutboundConfiguration {
    private static final String CHANNEL_CONNECTOR_SERVICE = "channel-connector-service";
    private static final String RISK_SERVICE = "risk-service";

    /** channel-connector MID 候选查询客户端；读取超时取候选查询配置。 */
    @Bean
    ChannelCandidateQueryApi channelCandidateQueryApi(RestClient.Builder builder,
        ObjectProvider<LoadBalancerClient> discovery, PaymentOutboundProperties properties,
        InternalTokenClientOptions internalToken) {
        RestClient client = client(builder, discovery, CHANNEL_CONNECTOR_SERVICE,
            properties.getChannelConnectorContextPath(), properties.getChannelCandidateReadTimeout());
        return HttpServiceClientFactory.create(client,
            internalToken.apply(HttpServiceClientOptions.strict().withStrictJsonResponses(65536), CHANNEL_CONNECTOR_SERVICE))
            .createClient(ChannelCandidateQueryApi.class);
    }

    /** channel-connector 渠道操作客户端；读取超时须大于 channel-connector 调用 Platform 的读取超时。 */
    @Bean
    ChannelOperationApi channelOperationApi(RestClient.Builder builder, ObjectProvider<LoadBalancerClient> discovery,
        PaymentOutboundProperties properties, InternalTokenClientOptions internalToken) {
        RestClient client = client(builder, discovery, CHANNEL_CONNECTOR_SERVICE,
            properties.getChannelConnectorContextPath(), properties.getChannelOperationReadTimeout());
        return HttpServiceClientFactory.create(client,
            internalToken.apply(HttpServiceClientOptions.strict().withStrictJsonResponses(65536), CHANNEL_CONNECTOR_SERVICE))
            .createClient(ChannelOperationApi.class);
    }

    /**
     * Risk 统一评估客户端。
     * <li>503 RISK_DECISION_NOT_CONFIGURED 与其他 5xx 一样按依赖不可用抛出，无需注册合同错误码。</li>
     */
    @Bean
    RiskDecisionApi riskDecisionApi(RestClient.Builder builder, ObjectProvider<LoadBalancerClient> discovery,
        PaymentOutboundProperties properties, InternalTokenClientOptions internalToken) {
        RestClient client = client(builder, discovery, RISK_SERVICE, properties.getRiskContextPath(), properties.getRiskReadTimeout());
        return HttpServiceClientFactory
            .create(client, internalToken.apply(HttpServiceClientOptions.strict().withStrictJsonResponses(8192), RISK_SERVICE))
            .createClient(RiskDecisionApi.class);
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
