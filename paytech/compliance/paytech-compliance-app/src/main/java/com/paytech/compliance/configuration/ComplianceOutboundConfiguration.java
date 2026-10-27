package com.paytech.compliance.configuration;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Arrays;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerRequestFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.paytech.channel.connector.api.enums.ServiceExecutionErrorCode;
import com.paytech.channel.connector.api.serviceexecution.ServiceChannelExecutionApi;
import com.paytech.client.master.api.checkout.ClientMasterCheckoutApi;
import com.paytech.compliance.core.configuration.ScreeningProperties;
import com.paytech.compliance.integration.ScreeningSearchGateway;
import com.paytech.compliance.integration.ScreeningSubjectDirectory;
import com.paytech.compliance.integration.http.ChannelConnectorScreeningGateway;
import com.paytech.compliance.integration.http.ClientMasterScreeningSubjectDirectory;
import com.paytech.foundation.httpclient.api.HttpServiceClientFactory;
import com.paytech.foundation.httpclient.api.HttpServiceClientOptions;
import com.paytech.foundation.internalauth.api.InternalTokenSupplier;

import tools.jackson.databind.json.JsonMapper;

/**
 * compliance 的出站 HTTP（名单筛查）：channel-connector 服务渠道执行与 client-master CM-13 资料读取
 * <li>生产经 LoadBalancerClient 按服务名发现；mock Profile 测试只允许 compliance.outbound.test-base-url 指向 127.0.0.1 的 HTTP 根路径。</li>
 * <li>JDK HttpClient：连接 2 秒、禁重定向；读超时 connector 10 秒、client-master 3 秒。只在补偿 Handler 的事务外调用。</li>
 * <li>internal-auth 开启时按目标服务名附加内部调用 Token。</li>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ScreeningProperties.class)
public class ComplianceOutboundConfiguration {
    /** channel-connector 服务发现名。 */
    static final String CONNECTOR = "channel-connector-service";

    /** client-master 服务发现名。 */
    static final String CLIENT_MASTER = "client-master-service";

    /** 名单筛查渠道调用。 */
    @Bean
    ScreeningSearchGateway screeningSearchGateway(Environment env, ObjectProvider<LoadBalancerClient> discovery,
        ObjectProvider<InternalTokenSupplier> tokens, JsonMapper jsonMapper) {
        ServiceChannelExecutionApi api = HttpServiceClientFactory.create(transport(env, discovery, CONNECTOR, Duration.ofSeconds(10)),
            withToken(HttpServiceClientOptions.strict().withStrictJsonResponses(262144), tokens, CONNECTOR),
            ServiceExecutionErrorCode.values()).createClient(ServiceChannelExecutionApi.class);
        return new ChannelConnectorScreeningGateway(api, jsonMapper);
    }

    /** 筛查主体资料读取。 */
    @Bean
    ScreeningSubjectDirectory screeningSubjectDirectory(Environment env, ObjectProvider<LoadBalancerClient> discovery,
        ObjectProvider<InternalTokenSupplier> tokens) {
        ClientMasterCheckoutApi api = HttpServiceClientFactory.create(transport(env, discovery, CLIENT_MASTER, Duration.ofSeconds(3)),
            withToken(HttpServiceClientOptions.strict().withStrictJsonResponses(1048576), tokens, CLIENT_MASTER))
            .createClient(ClientMasterCheckoutApi.class);
        return new ClientMasterScreeningSubjectDirectory(api);
    }

    /**
     * 按目标服务名附加内部调用 Token；internal-auth 关闭时原样返回
     *
     * @param options 原选项
     * @param tokens Token 来源
     * @param targetService 目标服务发现名
     * @return 附加后的选项
     */
    static HttpServiceClientOptions withToken(HttpServiceClientOptions options, ObjectProvider<InternalTokenSupplier> tokens,
        String targetService) {
        InternalTokenSupplier supplier = tokens.getIfAvailable();
        return supplier == null ? options : options.withInternalToken(supplier, targetService);
    }

    /**
     * 出站传输：生产服务发现，mock 测试只允许 loopback 根路径
     *
     * @param env 环境
     * @param discovery 负载均衡客户端
     * @param service 目标服务发现名
     * @param readTimeout 读超时
     * @return RestClient
     */
    static RestClient transport(Environment env, ObjectProvider<LoadBalancerClient> discovery, String service, Duration readTimeout) {
        String url = env.getProperty("compliance.outbound.test-base-url", "");
        RestClient.Builder builder = RestClient.builder();
        if (url.isBlank()) {
            builder.baseUrl("http://" + service).requestInterceptor((request, body, execution) -> {
                LoadBalancerClient balancer = discovery.getObject();
                var instance = balancer.choose(service);
                if (instance == null) {
                    throw new ResourceAccessException(service + " discovery is unavailable");
                }
                return balancer.execute(service, instance, new LoadBalancerRequestFactory(balancer).createRequest(request, body, execution));
            });
        } else {
            URI uri = URI.create(url);
            if (!Arrays.asList(env.getActiveProfiles()).contains("mock") || !"http".equals(uri.getScheme())
                || !"127.0.0.1".equals(uri.getHost()) || uri.getPort() < 1 || uri.getPort() > 65535 || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null || !uri.getPath().isEmpty()) {
                throw new IllegalArgumentException("出站测试直连仅允许 mock Profile 的 loopback HTTP 根路径");
            }
            builder.baseUrl(url);
        }
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(readTimeout);
        return builder.requestFactory(factory).build();
    }
}
