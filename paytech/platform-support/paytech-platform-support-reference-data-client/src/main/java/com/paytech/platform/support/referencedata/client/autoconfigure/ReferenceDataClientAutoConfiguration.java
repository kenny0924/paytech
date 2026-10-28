package com.paytech.platform.support.referencedata.client.autoconfigure;

import java.net.http.HttpClient;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.client.RestClient;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import com.paytech.foundation.httpclient.api.HttpServiceClientFactory;
import com.paytech.foundation.httpclient.api.HttpServiceClientOptions;
import com.paytech.foundation.internalauth.api.InternalTokenSupplier;
import com.paytech.platform.support.api.referencedata.CountrySubdivisionQueryApi;
import com.paytech.platform.support.api.referencedata.IndustryQueryApi;
import com.paytech.platform.support.api.referencedata.MccQueryApi;
import com.paytech.platform.support.api.referencedata.ReferenceCountryQueryApi;
import com.paytech.platform.support.api.referencedata.ReferenceCurrencyQueryApi;
import com.paytech.platform.support.referencedata.client.ReferenceData;
import com.paytech.platform.support.referencedata.client.ReferenceDataRefresher;
import com.paytech.platform.support.referencedata.client.internal.BoundedResponseInterceptor;
import com.paytech.platform.support.referencedata.client.internal.LoadBalancedPlatformTarget;
import com.paytech.platform.support.referencedata.client.internal.PlatformReferenceDataLoader;
import com.paytech.platform.support.referencedata.client.internal.ReferenceDataHealthIndicator;
import com.paytech.platform.support.referencedata.client.internal.ReferenceDataRefreshScheduler;
import com.paytech.platform.support.referencedata.client.internal.ReferenceDataSnapshot;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * 参考数据客户端装配：
 * <li>目标：配置 base-url 时直连；否则经宿主 LoadBalancerClient 按 service-id + context-path 访问（没有时启动失败）。</li>
 * <li>每个请求带 {@code X-Caller-Service}（caller-service，默认 spring.application.name），供 Platform internal-auth DUAL 识别调用服务。</li>
 * <li>组件：快照、刷新入口与自带调度、健康指标 {@code referenceData}，以及只供本客户端使用的 Platform HTTP 代理。</li>
 * <li>HTTP 客户端自建，不与宿主的 {@code platform-support-service} 组共用：宽松 JSON（忽略 Platform 追加的字段），响应上限 1 MiB，
 * 不自动重试、不跟随重定向；错误按 Foundation 公共出站边界（RFC 9457 Problem）转换。</li>
 * <li>宿主引入 {@code paytech-internal-auth} 且存在 {@link InternalTokenSupplier} Bean 时，附加目标为 service-id（默认
 * {@code platform-support-service}）的内部调用 Token，与 X-Caller-Service 同时发送；否则只发送 X-Caller-Service。</li>
 */
@AutoConfiguration(afterName = "org.springframework.cloud.loadbalancer.config.BlockingLoadBalancerClientAutoConfiguration")
@EnableConfigurationProperties(ReferenceDataClientProperties.class)
public class ReferenceDataClientAutoConfiguration {

    /** 调用服务请求头。 */
    static final String CALLER_SERVICE_HEADER = "X-Caller-Service";

    /** 已开启种类的进程内快照，即消费方注入的 {@link ReferenceData}。 */
    @Bean
    ReferenceDataSnapshot referenceDataSnapshot(ReferenceDataClientProperties properties) {
        return new ReferenceDataSnapshot(properties.kinds());
    }

    /** 刷新入口与自带调度；随应用上下文启动和关闭。 */
    @Bean
    ReferenceDataRefreshScheduler referenceDataRefresher(ReferenceDataSnapshot snapshot,
        PlatformReferenceDataLoader referenceDataLoader, ReferenceDataClientProperties properties) {
        return new ReferenceDataRefreshScheduler(snapshot, referenceDataLoader, properties.schedulingEnabled(),
            properties.refreshInterval(), properties.initialRetryInterval());
    }

    /** 健康指标，Bean 名即健康组件名 {@code referenceData}。 */
    @Bean
    ReferenceDataHealthIndicator referenceData(ReferenceDataSnapshot snapshot) {
        return new ReferenceDataHealthIndicator(snapshot);
    }

    /** Platform 五个只读查询接口的代理，只供刷新入口使用。 */
    @Bean
    PlatformReferenceDataLoader referenceDataLoader(ReferenceDataClientProperties properties, Environment environment,
        BeanFactory beanFactory, ObjectProvider<ReferenceDataHttpOptions> httpOptions) {
        HttpServiceClientOptions options = HttpServiceClientOptions.strict();
        ReferenceDataHttpOptions customizer = httpOptions.getIfAvailable();
        RestClient.Builder builder = baseBuilder(properties, resolveCaller(properties, environment));
        if (properties.baseUrl() == null) {
            builder.baseUrl(properties.discoveryBaseUrl())
                .requestInterceptor(LoadBalancedPlatformTarget.interceptor(beanFactory, properties.serviceId()));
        } else {
            builder.baseUrl(properties.baseUrl());
        }
        HttpServiceProxyFactory factory = HttpServiceClientFactory.create(builder.build(),
            customizer == null ? options : customizer.customize(options, properties.serviceId()));
        return new PlatformReferenceDataLoader(factory.createClient(ReferenceCurrencyQueryApi.class),
            factory.createClient(ReferenceCountryQueryApi.class), factory.createClient(CountrySubdivisionQueryApi.class),
            factory.createClient(IndustryQueryApi.class), factory.createClient(MccQueryApi.class));
    }

    /** 调用服务名：caller-service，未配置时取 spring.application.name；都没有时启动失败。 */
    private static String resolveCaller(ReferenceDataClientProperties properties, Environment environment) {
        String caller = properties.callerService() != null ? properties.callerService()
            : environment.getProperty("spring.application.name");
        if (caller == null) {
            throw new IllegalStateException("参考数据客户端缺少调用服务名：请配置 platform-support.reference-data.client.caller-service "
                + "或 spring.application.name");
        }
        ReferenceDataClientProperties.requireServiceName("caller-service", caller);
        return caller;
    }

    /**
     * 本客户端专用的 RestClient Builder（目标地址由调用方设置）
     * <li>{@code HttpServiceClientOptions.strict()} 只表示严格的 Problem 错误解码；未调用 withStrictJsonResponses，因此成功响应使用下列宽松
     * JsonMapper，未知字段被忽略。</li>
     */
    private static RestClient.Builder baseBuilder(ReferenceDataClientProperties properties, String caller) {
        HttpClient transport = HttpClient.newBuilder().connectTimeout(properties.connectTimeout())
            .followRedirects(HttpClient.Redirect.NEVER).build();
        var requestFactory = new JdkClientHttpRequestFactory(transport);
        requestFactory.setReadTimeout(properties.readTimeout());
        JsonMapper json = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        return RestClient.builder().requestFactory(requestFactory).defaultHeader(CALLER_SERVICE_HEADER, caller)
            .requestInterceptor(new BoundedResponseInterceptor())
            .configureMessageConverters(converters -> converters.disableDefaults()
                .withJsonConverter(new JacksonJsonHttpMessageConverter(json)));
    }

    /** 调整本客户端的 HTTP 选项（内部调用 Token）。 */
    @FunctionalInterface
    interface ReferenceDataHttpOptions {

        /**
         * 在基础选项上附加宿主能力
         *
         * @param options 基础选项
         * @param targetService Platform 服务发现名（Token {@code aud}）
         * @return 调整后的选项
         */
        HttpServiceClientOptions customize(HttpServiceClientOptions options, String targetService);
    }

    /** 仅当宿主引入 {@code paytech-internal-auth} 时加载；没有 Supplier Bean 时选项不变。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "com.paytech.foundation.internalauth.api.InternalTokenSupplier")
    static class InternalTokenConfiguration {

        /** 存在 {@link InternalTokenSupplier} 时为 Platform 请求附加内部调用 Token。 */
        @Bean
        ReferenceDataHttpOptions referenceDataInternalToken(ObjectProvider<InternalTokenSupplier> tokens) {
            return (options, targetService) -> {
                InternalTokenSupplier supplier = tokens.getIfAvailable();
                return supplier == null ? options : options.withInternalToken(supplier, targetService);
            };
        }
    }
}
