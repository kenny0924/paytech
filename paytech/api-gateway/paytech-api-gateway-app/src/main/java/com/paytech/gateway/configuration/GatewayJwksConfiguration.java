package com.paytech.gateway.configuration;

import java.time.Duration;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.reactive.ReactorLoadBalancerExchangeFilterFunction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import io.netty.channel.ChannelOption;
import reactor.netty.http.client.HttpClient;

/** 通过 Nacos 服务发现装配非阻塞的 Operations JWKS 客户端。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PlatformJwksProperties.class)
public class GatewayJwksConfiguration {
    static final int MAX_RESPONSE_BYTES = 64 * 1024;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(3);

    @Bean
    @Qualifier("operationsJwksWebClient")
    WebClient operationsJwksWebClient(PlatformJwksProperties properties,
        ObjectProvider<ReactorLoadBalancerExchangeFilterFunction> loadBalancerProvider) {
        HttpClient httpClient = HttpClient.create().disableRetry(true).followRedirect(false)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(CONNECT_TIMEOUT.toMillis()))
            .responseTimeout(RESPONSE_TIMEOUT);
        WebClient.Builder builder =
            WebClient.builder().baseUrl(baseUrl(properties)).clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_RESPONSE_BYTES));
        if (properties.discoveryEnabled()) {
            ReactorLoadBalancerExchangeFilterFunction loadBalancer = loadBalancerProvider.getIfAvailable();
            if (loadBalancer == null) {
                throw new IllegalStateException("Gateway Platform JWKS 服务发现不可用");
            }
            builder.filter(loadBalancer);
        }
        return builder.build();
    }

    private String baseUrl(PlatformJwksProperties properties) {
        return properties.discoveryEnabled() ? "http://" + properties.serviceId() : properties.baseUri().toString();
    }
}
