package com.paytech.platform.support.configuration;

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

import com.paytech.foundation.httpclient.api.HttpServiceClientFactory;
import com.paytech.foundation.httpclient.api.HttpServiceClientOptions;
import com.paytech.payment.api.card.ChannelCardMaterialApi;
import com.paytech.platform.support.integration.core.configuration.ExternalApiExecutionProperties;

/**
 * 卡直付执行读卡客户端（PayerMax 卡直付对接技术方案第 2、3.1 节）：经私网服务发现调用 Payment（含其 context path），不跟随重定向。
 *
 * <p>
 * 响应含卡原值：Foundation 出站客户端不记录报文，调用方只在内存中使用。读卡超时 2 秒，受流程总预算约束。
 */
@Configuration(proxyBeanMethods = false)
public class PaymentCardMaterialConfiguration {

    private static final String PAYMENT_SERVICE = "payment-service";

    /** Payment 读卡接口客户端；internal-auth.mode 非 OFF 时附加访问 Payment 的内部 Token。 */
    @Bean
    ChannelCardMaterialApi channelCardMaterialApi(ObjectProvider<LoadBalancerClient> discovery,
        ExternalApiExecutionProperties properties, InternalTokenClientOptions internalToken) {
        String baseUrl = "http://" + PAYMENT_SERVICE + properties.getPaymentContextPath();
        var builder = RestClient.builder().baseUrl(baseUrl).requestInterceptor((request, body, execution) -> {
            var balancer = discovery.getIfAvailable();
            var instance = balancer == null ? null : balancer.choose(PAYMENT_SERVICE);
            if (instance == null) {
                throw new ResourceAccessException("Payment discovery unavailable");
            }
            return balancer.execute(PAYMENT_SERVICE, instance,
                new LoadBalancerRequestFactory(balancer).createRequest(request, body, execution));
        });
        var transport = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1))
            .followRedirects(HttpClient.Redirect.NEVER).build());
        transport.setReadTimeout(Duration.ofSeconds(2));
        return HttpServiceClientFactory
            .create(builder.requestFactory(transport).build(),
                internalToken.apply(HttpServiceClientOptions.strict().withStrictJsonResponses(8192), PAYMENT_SERVICE))
            .createClient(ChannelCardMaterialApi.class);
    }
}
