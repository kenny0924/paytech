package com.paytech.gateway.configuration;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.reactive.ReactorLoadBalancerExchangeFilterFunction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.json.JacksonJsonDecoder;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import com.paytech.gateway.observability.GatewayMetrics;
import com.paytech.gateway.routing.snapshot.*;

import io.netty.channel.ChannelOption;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.resources.LoopResources;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.json.JsonMapper;

/** 装配有界的 PLT-I20 客户端、编译器与原子 Last Valid 存储。 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(PlatformSnapshotProperties.class)
public class GatewaySnapshotConfiguration {
    @Bean
    GatewaySnapshotStore gatewaySnapshotStore() {
        return new GatewaySnapshotStore();
    }

    /** 装配通用目标校验器，不注入业务服务或路径注册表。 */
    @Bean
    GatewaySnapshotCompiler gatewaySnapshotCompiler() {
        return new GatewaySnapshotCompiler();
    }

    @Bean(destroyMethod = "dispose")
    ConnectionProvider platformSnapshotConnectionProvider(PlatformSnapshotProperties properties) {
        return ConnectionProvider.builder("gateway-platform-snapshot").maxConnections(2).pendingAcquireMaxCount(4)
            .pendingAcquireTimeout(properties.connectTimeout()).maxIdleTime(properties.responseTimeout()).build();
    }

    @Bean(destroyMethod = "dispose")
    LoopResources platformSnapshotLoopResources() {
        return LoopResources.create("gateway-platform-snapshot", 1, true);
    }

    @Bean
    ReactiveGatewaySnapshotClient reactiveGatewaySnapshotClient(PlatformSnapshotProperties properties,
        ConnectionProvider platformSnapshotConnectionProvider, LoopResources platformSnapshotLoopResources,
        ObjectProvider<ReactorLoadBalancerExchangeFilterFunction> loadBalancerProvider) {
        HttpClient httpClient =
            HttpClient.create(platformSnapshotConnectionProvider).runOn(platformSnapshotLoopResources)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(properties.connectTimeout().toMillis()))
                .responseTimeout(properties.responseTimeout());
        ExchangeStrategies strategies = ExchangeStrategies.builder().codecs(codecs -> {
            codecs.defaultCodecs().maxInMemorySize(properties.maxResponseBytes());
            codecs.defaultCodecs().jacksonJsonDecoder(new JacksonJsonDecoder(snapshotObjectMapper()));
        }).build();
        WebClient.Builder builder = WebClient.builder().baseUrl(snapshotBaseUrl(properties))
            .clientConnector(new ReactorClientHttpConnector(httpClient)).exchangeStrategies(strategies);
        if (properties.discoveryEnabled()) {
            ReactorLoadBalancerExchangeFilterFunction loadBalancer = loadBalancerProvider.getIfAvailable();
            if (loadBalancer == null) {
                throw new IllegalStateException("Gateway Platform Snapshot 服务发现不可用");
            }
            builder.filter(loadBalancer);
        }
        WebClient webClient = builder.build();
        return new PlatformGatewaySnapshotClient(webClient, properties.responseTimeout());
    }

    private String snapshotBaseUrl(PlatformSnapshotProperties properties) {
        if (properties.discoveryEnabled()) {
            return "http://" + properties.serviceId();
        }
        return properties.baseUri().toString();
    }

    JsonMapper snapshotObjectMapper() {
        StreamReadConstraints constraints =
            StreamReadConstraints.builder().maxDocumentLength(PlatformSnapshotProperties.MAX_RESPONSE_BYTES)
                .maxNestingDepth(PlatformSnapshotProperties.MAX_JSON_NESTING_DEPTH).build();
        JsonFactory factory = JsonFactory.builder().streamReadConstraints(constraints)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
        return JsonMapper.builder(factory).build();
    }

    @Bean
    GatewaySnapshotRefresher gatewaySnapshotRefresher(ReactiveGatewaySnapshotClient client,
        GatewaySnapshotCompiler compiler, GatewaySnapshotStore store, GatewayMetrics metrics) {
        return new GatewaySnapshotRefresher(client, compiler, store, metrics);
    }
}
