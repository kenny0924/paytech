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

import com.paytech.foundation.httpclient.api.HttpServiceClientFactory;
import com.paytech.foundation.httpclient.api.HttpServiceClientOptions;
import com.paytech.platform.support.api.enums.StandardApiExecutionErrorCode;
import com.paytech.platform.support.api.standardapi.StandardApiCommandApi;
import com.paytech.risk.api.evaluation.RiskDecisionApi;

/**
 * Execution 出站 HTTP 客户端：经私网服务发现调用 Platform 标准 API 与 Risk 统一评估，不跟随重定向、不自动重试。
 * <li>出站客户端不记录报文；Platform 执行可能已发出渠道请求，超时由调用方按结果未知处理。</li>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PaymentOutboundProperties.class)
public class PaymentOutboundConfiguration {
    private static final String PLATFORM_SERVICE = "platform-support-service";
    private static final String RISK_SERVICE = "risk-service";

    /** Platform 标准 API 内部执行客户端。 */
    @Bean
    StandardApiCommandApi standardApiCommandApi(RestClient.Builder builder, ObjectProvider<LoadBalancerClient> discovery,
        PaymentOutboundProperties properties) {
        RestClient client = client(builder, discovery, PLATFORM_SERVICE, properties.getPlatformContextPath(),
            properties.getPlatformReadTimeout());
        return HttpServiceClientFactory
            .create(client, HttpServiceClientOptions.strict().withStrictJsonResponses(65536),
                StandardApiExecutionErrorCode.values())
            .createClient(StandardApiCommandApi.class);
    }

    /**
     * Risk 统一评估客户端。
     * <li>503 RISK_DECISION_NOT_CONFIGURED 与其他 5xx 一样按依赖不可用抛出，无需注册合同错误码。</li>
     */
    @Bean
    RiskDecisionApi riskDecisionApi(RestClient.Builder builder, ObjectProvider<LoadBalancerClient> discovery,
        PaymentOutboundProperties properties) {
        RestClient client = client(builder, discovery, RISK_SERVICE, properties.getRiskContextPath(), properties.getRiskReadTimeout());
        return HttpServiceClientFactory.create(client, HttpServiceClientOptions.strict().withStrictJsonResponses(8192))
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
