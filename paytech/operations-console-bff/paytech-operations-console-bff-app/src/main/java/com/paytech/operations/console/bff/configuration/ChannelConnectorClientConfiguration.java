package com.paytech.operations.console.bff.configuration;

import java.net.http.HttpClient;
import java.time.Duration;

import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import com.paytech.channel.connector.api.change.ChannelConfigChangeApi;
import com.paytech.channel.connector.api.channel.ChannelAccountApi;
import com.paytech.channel.connector.api.channel.ChannelMidApi;
import com.paytech.channel.connector.api.channel.MidCapabilityApi;
import com.paytech.channel.connector.api.channel.PaymentChannelApi;
import com.paytech.channel.connector.api.cost.ChannelCostPlanApi;
import com.paytech.channel.connector.api.enums.ChannelCostErrorCode;
import com.paytech.channel.connector.api.externalapi.ExternalApiEditorApi;
import com.paytech.channel.connector.api.externalapi.IntegrationApiManagementApi;
import com.paytech.channel.connector.api.externalapi.IntegrationImplementationManagementApi;
import com.paytech.channel.connector.api.externalapi.IntegrationManagementApi;
import com.paytech.channel.connector.api.externalapi.IntegrationScriptManagementApi;
import com.paytech.channel.connector.api.externalapi.IntegrationVerifyManagementApi;
import com.paytech.channel.connector.api.externalapi.StandardApiManagementApi;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.httpclient.api.HttpServiceClientFactory;
import com.paytech.foundation.httpclient.api.HttpServiceClientOptions;
import com.paytech.foundation.internalauth.api.InternalTokenSupplier;
import com.paytech.foundation.web.request.RequestIdFilter;
import com.paytech.operations.console.bff.domain.enums.OperationsBffErrorCode;
import com.paytech.operations.console.bff.integration.OwnerDiscoveryInterceptor;

/**
 * channel-connector 的 HTTP 接口代理：Nacos 发现 channel-connector-service，auto 测试改为 loopback 直连
 * <li>连接 2 秒、读取 4 秒、禁止跟随重定向、不重试；传递 X-Request-Id 与 X-Caller-Service。</li>
 * <li>严格 Problem 解码并保留 Owner 422 字段路径；显式开启 Foundation 的 Owner Problem detail：业务拒绝、并发冲突、NOT_FOUND
 * 与已登记合同码把 channel-connector 已校验的 detail（例如“Platform 不存在同编码接入方”）作为本地异常消息到达浏览器；
 * INVALID_REQUEST、字段校验、5xx 与协议错误不变。超时映射 504，不可达或 503 映射 DEPENDENCY_UNAVAILABLE。</li>
 * <li>登记 api 模块唯一的 CodeMessageEnum（ChannelCostErrorCode）；管理接口目前只返回公共错误码，登记使未来该码到达时保留 Owner
 * 原因而不是降级为 500。</li>
 * <li>外部 API 配置管理（/external-api-admin/v1，2026-10-20 自 Platform 迁入）使用独立工厂，见 {@link #externalApiFactory}。</li>
 * <li>工厂不注册为 Bean，避免被其他 Owner 代理按类型误用。</li>
 *
 * @author Claude
 */
@Configuration(proxyBeanMethods = false)
public class ChannelConnectorClientConfiguration {
    /** channel-connector 的服务发现名与内部 Token 目标。 */
    static final String CHANNEL_CONNECTOR_SERVICE = "channel-connector-service";

    /** 外部 API 配置管理沿用迁移前 Platform 客户端的连接超时。 */
    private static final Duration EXTERNAL_API_CONNECT_TIMEOUT = Duration.ofSeconds(3);

    /** 外部 API 配置管理沿用迁移前 Platform 客户端的读取超时（流程与脚本静态校验比渠道配置读写更重）。 */
    private static final Duration EXTERNAL_API_READ_TIMEOUT = Duration.ofSeconds(5);

    private final HttpServiceProxyFactory channelConnector;

    private final HttpServiceProxyFactory externalApi;

    /** 发现模式或 auto 测试直连地址（只允许 auto Profile 下的 loopback）。 */
    public ChannelConnectorClientConfiguration(Environment environment, LoadBalancerClient loadBalancer,
        @Value("${operations.channel-connector-test-base-url:}") String testBaseUrl,
        ObjectProvider<InternalTokenSupplier> tokens) {
        this.channelConnector = factory(restClient(environment, loadBalancer, testBaseUrl,
            ClientReviewClientConfiguration.requestFactory()), tokens);
        this.externalApi = externalApiFactory(restClient(environment, loadBalancer, testBaseUrl,
            externalApiRequestFactory()), tokens);
    }

    /** 渠道成本方案与版本（列表、详情、Maker-Checker、作废）。 */
    @Bean
    ChannelCostPlanApi channelCostPlanApi() {
        return channelConnector.createClient(ChannelCostPlanApi.class);
    }

    /** 渠道配置与成本版本的变更记录。 */
    @Bean
    ChannelConfigChangeApi channelConfigChangeApi() {
        return channelConnector.createClient(ChannelConfigChangeApi.class);
    }

    /** 支付渠道。 */
    @Bean
    PaymentChannelApi paymentChannelApi() {
        return channelConnector.createClient(PaymentChannelApi.class);
    }

    /** 渠道技术账号。 */
    @Bean
    ChannelAccountApi channelAccountApi() {
        return channelConnector.createClient(ChannelAccountApi.class);
    }

    /** MID。 */
    @Bean
    ChannelMidApi channelMidApi() {
        return channelConnector.createClient(ChannelMidApi.class);
    }

    /** MID 能力条件组与能力目录。 */
    @Bean
    MidCapabilityApi midCapabilityApi() {
        return channelConnector.createClient(MidCapabilityApi.class);
    }

    /** 标准 API 管理（E01～E05）。 */
    @Bean
    StandardApiManagementApi standardApiManagementApi() {
        return externalApi.createClient(StandardApiManagementApi.class);
    }

    /** 接入方管理（E06～E10）。 */
    @Bean
    IntegrationManagementApi integrationManagementApi() {
        return externalApi.createClient(IntegrationManagementApi.class);
    }

    /** 原子 API 管理与静态校验（E11～E16）。 */
    @Bean
    IntegrationApiManagementApi integrationApiManagementApi() {
        return externalApi.createClient(IntegrationApiManagementApi.class);
    }

    /** 公共脚本管理与静态校验（E17～E22）。 */
    @Bean
    IntegrationScriptManagementApi integrationScriptManagementApi() {
        return externalApi.createClient(IntegrationScriptManagementApi.class);
    }

    /** 渠道标准 API 实现管理与静态校验（E23～E28）。 */
    @Bean
    IntegrationImplementationManagementApi integrationImplementationManagementApi() {
        return externalApi.createClient(IntegrationImplementationManagementApi.class);
    }

    /** 流程编辑器上下文与编辑器能力（E29～E30）。 */
    @Bean
    ExternalApiEditorApi externalApiEditorApi() {
        return externalApi.createClient(ExternalApiEditorApi.class);
    }

    /** 渠道沙箱验证账号、用例与运行（V01～V14）。 */
    @Bean
    IntegrationVerifyManagementApi integrationVerifyManagementApi() {
        return externalApi.createClient(IntegrationVerifyManagementApi.class);
    }

    /** 发现或直连，使用指定超时，传递 Request ID 与调用方标识。 */
    private static RestClient restClient(Environment environment, LoadBalancerClient loadBalancer, String testBaseUrl,
        ClientHttpRequestFactory requestFactory) {
        RestClient.Builder builder = RestClient.builder();
        if (testBaseUrl.isBlank()) {
            builder.baseUrl("http://" + CHANNEL_CONNECTOR_SERVICE)
                .requestInterceptor(new OwnerDiscoveryInterceptor(loadBalancer, CHANNEL_CONNECTOR_SERVICE));
        } else {
            ClientReviewClientConfiguration.validateTestAddress(testBaseUrl, environment);
            builder.baseUrl(testBaseUrl);
        }
        return builder.requestFactory(requestFactory)
            .requestInterceptor((request, body, execution) -> {
                String requestId = MDC.get(RequestIdFilter.MDC_KEY);
                if (requestId != null) {
                    request.getHeaders().set(RequestIdFilter.HEADER_NAME, requestId);
                }
                request.getHeaders().set("X-Caller-Service", CommercialClientConfiguration.CALLER_SERVICE);
                return execution.execute(request, body);
            }).build();
    }

    /** 严格协议、Owner detail 外显、登记合同错误码并映射依赖故障。 */
    private static HttpServiceProxyFactory factory(RestClient client, ObjectProvider<InternalTokenSupplier> tokens) {
        HttpServiceClientOptions options = InternalCallTokenOptions.apply(
            HttpServiceClientOptions.strict().withRequestValidationFailures().withOwnerProblemDetail(), tokens,
            CHANNEL_CONNECTOR_SERVICE)
            .withDependencyFailureMapper((kind, cause) -> switch (kind) {
                case TIMEOUT -> new ErrorException(OperationsBffErrorCode.DEPENDENCY_TIMEOUT, null, cause);
                case UNAVAILABLE -> new ServiceUnavailableException(OperationsBffErrorCode.DEPENDENCY_UNAVAILABLE, cause);
            });
        return HttpServiceClientFactory.create(client, options, ChannelCostErrorCode.values());
    }

    /**
     * 外部 API 配置管理的代理工厂：让浏览器看到的结果与迁移前经 Platform 时一致
     * <li>不开启 Owner Problem detail：迁移前 Platform 工厂未开启，业务拒绝（400）、NOT_FOUND、并发冲突（409）以 BFF 公共默认文案到达浏览器；
     * 渠道配置工厂开启了该开关，若复用会把 channel-connector 的业务 detail 外显，改变页面提示。</li>
     * <li>开启 422 字段校验接收，由 ExternalApiValidationConverter 按本次请求体投影字段路径，说明统一为安全文案。</li>
     * <li>严格 Problem 解码：channel-connector 只输出 RFC 9457 Problem；迁移前的旧四字段兼容只为 Platform 历史接口保留，本组接口从未使用。</li>
     * <li>不登记额外合同错误码：迁移前登记的 Operations/Gateway 错误码来自 Platform 的会话与权限复核，channel-connector 不再复核也不会返回；
     * 公共码（BUSINESS_CHECK_FAILED、NOT_FOUND、CONCURRENT_REQUEST、VALIDATION_ERROR、SERVICE_UNAVAILABLE）由 Foundation 统一识别。</li>
     * <li>连接 3 秒、读取 5 秒，与迁移前一致；超时映射 504，不可达或 503 映射 DEPENDENCY_UNAVAILABLE。</li>
     */
    private static HttpServiceProxyFactory externalApiFactory(RestClient client, ObjectProvider<InternalTokenSupplier> tokens) {
        HttpServiceClientOptions options = InternalCallTokenOptions.apply(
            HttpServiceClientOptions.strict().withRequestValidationFailures(), tokens, CHANNEL_CONNECTOR_SERVICE)
            .withDependencyFailureMapper((kind, cause) -> switch (kind) {
                case TIMEOUT -> new ErrorException(OperationsBffErrorCode.DEPENDENCY_TIMEOUT, null, cause);
                case UNAVAILABLE -> new ServiceUnavailableException(OperationsBffErrorCode.DEPENDENCY_UNAVAILABLE, cause);
            });
        return HttpServiceClientFactory.create(client, options);
    }

    /** 外部 API 配置管理的连接与读取超时，禁止跟随重定向。 */
    private static JdkClientHttpRequestFactory externalApiRequestFactory() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(EXTERNAL_API_CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(EXTERNAL_API_READ_TIMEOUT);
        return factory;
    }
}
