package com.paytech.operations.console.bff.configuration;

import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestClient;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import com.paytech.channel.connector.api.change.ChannelConfigChangeApi;
import com.paytech.channel.connector.api.channel.ChannelAccountApi;
import com.paytech.channel.connector.api.channel.ChannelMidApi;
import com.paytech.channel.connector.api.channel.MidCapabilityApi;
import com.paytech.channel.connector.api.channel.PaymentChannelApi;
import com.paytech.channel.connector.api.cost.ChannelCostPlanApi;
import com.paytech.channel.connector.api.enums.ChannelCostErrorCode;
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
 * <li>工厂不注册为 Bean，避免被其他 Owner 代理按类型误用。</li>
 *
 * @author Claude
 */
@Configuration(proxyBeanMethods = false)
public class ChannelConnectorClientConfiguration {
    /** channel-connector 的服务发现名与内部 Token 目标。 */
    static final String CHANNEL_CONNECTOR_SERVICE = "channel-connector-service";

    private final HttpServiceProxyFactory channelConnector;

    /** 发现模式或 auto 测试直连地址（只允许 auto Profile 下的 loopback）。 */
    public ChannelConnectorClientConfiguration(Environment environment, LoadBalancerClient loadBalancer,
        @Value("${operations.channel-connector-test-base-url:}") String testBaseUrl,
        ObjectProvider<InternalTokenSupplier> tokens) {
        this.channelConnector = factory(restClient(environment, loadBalancer, testBaseUrl), tokens);
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

    /** 发现或直连，传递 Request ID 与调用方标识。 */
    private static RestClient restClient(Environment environment, LoadBalancerClient loadBalancer, String testBaseUrl) {
        RestClient.Builder builder = RestClient.builder();
        if (testBaseUrl.isBlank()) {
            builder.baseUrl("http://" + CHANNEL_CONNECTOR_SERVICE)
                .requestInterceptor(new OwnerDiscoveryInterceptor(loadBalancer, CHANNEL_CONNECTOR_SERVICE));
        } else {
            ClientReviewClientConfiguration.validateTestAddress(testBaseUrl, environment);
            builder.baseUrl(testBaseUrl);
        }
        return builder.requestFactory(ClientReviewClientConfiguration.requestFactory())
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
}
