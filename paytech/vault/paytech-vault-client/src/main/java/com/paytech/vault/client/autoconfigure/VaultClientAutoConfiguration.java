package com.paytech.vault.client.autoconfigure;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Arrays;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import org.springframework.web.util.DefaultUriBuilderFactory;

import com.paytech.vault.client.VaultClient;
import com.paytech.vault.client.admin.VaultAdminClient;
import com.paytech.vault.client.internal.BoundedResponseBody;
import com.paytech.vault.client.internal.DefaultVaultAdminClient;
import com.paytech.vault.client.internal.DefaultVaultClient;
import com.paytech.vault.client.internal.VaultAdminHttpService;
import com.paytech.vault.client.internal.VaultHttpService;
import com.paytech.vault.client.internal.VaultRequestHeaders;

import tools.jackson.databind.json.JsonMapper;

/**
 * 复用宿主 Boot Builder/Observation，但冻结 Vault 专属安全传输约束。
 * 不进行逻辑 HTTP/应用重试、不安装 Retry 拦截器；JDK 可能重连或重发对端声明未处理的请求，
 * 其行为没有受支持的单客户端关闭开关，本组件不修改 JVM 全局属性。
 */
@AutoConfiguration(after = RestClientAutoConfiguration.class,
        afterName = "org.springframework.cloud.loadbalancer.config.BlockingLoadBalancerClientAutoConfiguration")
@EnableConfigurationProperties(VaultClientProperties.class)
@ConditionalOnMissingBean(VaultClient.class)
public class VaultClientAutoConfiguration {
    private static String resolveCaller(VaultClientProperties properties, Environment environment) {
        String caller = properties.callerService() == null ? environment.getProperty("spring.application.name")
            : properties.callerService();
        validateService(caller);
        return caller;
    }

    private static RestClient.Builder createClientBuilder(RestClient.Builder managedBuilder, HttpClient transport,
        Duration readTimeout, String baseUrl) {
        var factory = new JdkClientHttpRequestFactory(transport);
        // Framework 的 Duration 接口最终使用毫秒；向上取整避免正亚毫秒值变成无限等待。
        factory.setReadTimeout(Duration.ofMillis((readTimeout.toNanos() + 999999) / 1000000));
        return managedBuilder.clone().uriBuilderFactory(new DefaultUriBuilderFactory(baseUrl)).requestFactory(factory)
            .bufferContent((uri, method) -> false);
    }

    private static void configureInterceptors(RestClient.Builder builder, VaultRequestHeaders requestHeaders,
        VaultClientProperties properties, ObjectProvider<LoadBalancerClient> loadBalancer) {
        builder.requestInterceptors(interceptors -> {
            // 保留 Builder 的 Observation；禁止继承宿主的 Body Logger、缓存或 Retry 拦截器。
            interceptors.clear();
            interceptors.add(new BoundedResponseBody(requestHeaders));
            if (properties.baseUrl() == null) {
                interceptors.add(new LoadBalancerInterceptor(loadBalancer.getObject()));
            }
        });
    }

    private static String resolveBaseUrl(VaultClientProperties properties, Environment environment) {
        String service = properties.serviceId() == null ? "vault-service" : properties.serviceId();
        validateService(service);
        if (properties.baseUrl() == null) {
            return "http://" + service;
        }
        boolean test = Arrays.stream(environment.getActiveProfiles())
            .anyMatch(profile -> profile.equals("test") || profile.equals("unittest"));
        if (!test || environment.containsProperty("vault.client.service-id")) {
            throw invalidConfiguration();
        }
        try {
            URI uri = URI.create(properties.baseUrl());
            if (!"http".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))) {
                throw invalidConfiguration();
            }
            return properties.baseUrl();
        } catch (IllegalArgumentException ignored) {
            throw invalidConfiguration();
        }
    }

    private static void validateService(String value) {
        if (value == null || !value.matches("[a-z][a-z0-9-]{0,127}")) {
            throw invalidConfiguration();
        }
    }

    private static void configureProtocol(RestClient.Builder builder) {
        builder.defaultHeaders(headers -> {
            headers.remove("X-Caller-Service");
            headers.remove("Idempotency-Key");
            headers.remove("X-Request-Id");
            headers.remove("X-Operator-Id");
        });
        builder.configureMessageConverters(converters -> converters.disableDefaults()
            .withJsonConverter(new JacksonJsonHttpMessageConverter(JsonMapper.builder().build())));
    }

    private static void validateTimeout(Duration value) {
        if (value == null || value.isZero() || value.isNegative() || value.compareTo(Duration.ofSeconds(30)) > 0) {
            throw invalidConfiguration();
        }
    }

    @Bean(destroyMethod = "close")
    HttpClient vaultClientHttpTransport(VaultClientProperties properties) {
        validateTimeout(properties.connectTimeout());
        validateTimeout(properties.readTimeout());
        return HttpClient.newBuilder().connectTimeout(properties.connectTimeout())
            .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Bean
    VaultClient vaultClient(VaultClientProperties properties, Environment environment,
        RestClient.Builder managedBuilder, ObjectProvider<LoadBalancerClient> loadBalancer,
        @Qualifier("vaultClientHttpTransport") HttpClient transport) {
        String caller = resolveCaller(properties, environment);
        var service = createService(VaultHttpService.class, caller, properties, environment, managedBuilder,
            loadBalancer, transport);
        return new DefaultVaultClient(caller, service);
    }

    /**
     * 管理接口客户端，仅在 {@code vault.client.admin-enabled=true} 时创建（Operations BFF）；
     * 与业务客户端共用同一套调用者、目标、超时、有界读取与错误转换约束。
     */
    @Bean
    @ConditionalOnBooleanProperty("vault.client.admin-enabled")
    VaultAdminClient vaultAdminClient(VaultClientProperties properties, Environment environment,
        RestClient.Builder managedBuilder, ObjectProvider<LoadBalancerClient> loadBalancer,
        @Qualifier("vaultClientHttpTransport") HttpClient transport) {
        String caller = resolveCaller(properties, environment);
        return new DefaultVaultAdminClient(createService(VaultAdminHttpService.class, caller, properties, environment,
            managedBuilder, loadBalancer, transport));
    }

    /** 按 Vault 安全传输约束构建指定 HTTP 契约的代理。 */
    private static <T> T createService(Class<T> type, String caller, VaultClientProperties properties,
        Environment environment, RestClient.Builder managedBuilder, ObjectProvider<LoadBalancerClient> loadBalancer,
        HttpClient transport) {
        String baseUrl = resolveBaseUrl(properties, environment);
        var requestHeaders = new VaultRequestHeaders(caller);
        RestClient.Builder builder = createClientBuilder(managedBuilder, transport, properties.readTimeout(), baseUrl);
        configureProtocol(builder);
        configureInterceptors(builder, requestHeaders, properties, loadBalancer);
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(builder.build()))
            .customArgumentResolver(requestHeaders).build().createClient(type);
    }

    private static IllegalArgumentException invalidConfiguration() {
        return new IllegalArgumentException("Vault 客户端配置无效，请检查调用者、目标模式和有界超时");
    }
}
