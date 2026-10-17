package com.paytech.operations.console.bff.configuration;

import java.net.HttpURLConnection;
import java.net.URI;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;

import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.paytech.client.contract.api.enums.ClientDraftErrorCode;
import com.paytech.client.contract.api.onboarding.ClientApplicationAdminApi;
import com.paytech.client.contract.api.onboarding.ClientClassificationAdminApi;
import com.paytech.foundation.common.enums.CodeMessageEnum;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.httpclient.api.HttpServiceClientFactory;
import com.paytech.foundation.httpclient.api.HttpServiceClientOptions;
import com.paytech.foundation.internalauth.api.InternalTokenSupplier;
import com.paytech.foundation.web.request.RequestIdFilter;
import com.paytech.operations.console.bff.domain.enums.OperationsBffErrorCode;
import com.paytech.operations.console.bff.integration.OwnerDiscoveryInterceptor;
import com.paytech.risk.api.compliance.ClientReviewAdminApi;
import com.paytech.risk.api.enums.ClientReviewErrorCode;

/**
 * Client 入驻审核的 Risk 与 CCT 代理：独立工厂，使用默认 JSON 读取，不并入决策配置的响应形状门禁
 * <li>发现、超时、禁止重定向与 Request ID 传递与既有 Risk 代理一致；不重试。</li>
 * <li>只登记两方审核合同的错误码，其他 4xx 按上游响应无效处理。</li>
 */
@Configuration(proxyBeanMethods = false)
public class ClientReviewClientConfiguration {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(4);

    /** Risk 审核任务与结论。 */
    @Bean
    ClientReviewAdminApi clientReviewAdminApi(Environment environment, LoadBalancerClient loadBalancer,
        @Value("${operations.client-review-risk-test-base-url:}") String testBaseUrl,
        ObjectProvider<InternalTokenSupplier> tokens) {
        return factory(environment, loadBalancer, "risk-service", testBaseUrl, tokens, ClientReviewErrorCode.values())
            .createClient(ClientReviewAdminApi.class);
    }

    /** CCT 审核申请资料。 */
    @Bean
    ClientApplicationAdminApi clientApplicationAdminApi(Environment environment, LoadBalancerClient loadBalancer,
        @Value("${operations.cct-test-base-url:}") String testBaseUrl, ObjectProvider<InternalTokenSupplier> tokens) {
        return factory(environment, loadBalancer, "client-contract-service", testBaseUrl, tokens,
            ClientDraftErrorCode.values())
            .createClient(ClientApplicationAdminApi.class);
    }

    /** CCT 行业与 MCC 分类。 */
    @Bean
    ClientClassificationAdminApi clientClassificationAdminApi(Environment environment, LoadBalancerClient loadBalancer,
        @Value("${operations.cct-test-base-url:}") String testBaseUrl, ObjectProvider<InternalTokenSupplier> tokens) {
        return factory(environment, loadBalancer, "client-contract-service", testBaseUrl, tokens,
            ClientDraftErrorCode.values())
            .createClient(ClientClassificationAdminApi.class);
    }

    /** 发现或 auto 测试直连地址，严格协议并映射依赖故障。 */
    static org.springframework.web.service.invoker.HttpServiceProxyFactory factory(Environment environment,
        LoadBalancerClient loadBalancer, String serviceId, String testBaseUrl, ObjectProvider<InternalTokenSupplier> tokens,
        CodeMessageEnum[] codes) {
        RestClient.Builder builder = RestClient.builder();
        if (testBaseUrl.isBlank()) {
            builder.baseUrl("http://" + serviceId).requestInterceptor(new OwnerDiscoveryInterceptor(loadBalancer, serviceId));
        } else {
            validateTestAddress(testBaseUrl, environment);
            builder.baseUrl(testBaseUrl);
        }
        RestClient client = builder.requestFactory(requestFactory()).requestInterceptor((request, body, execution) -> {
            String requestId = MDC.get(RequestIdFilter.MDC_KEY);
            if (requestId != null) {
                request.getHeaders().set(RequestIdFilter.HEADER_NAME, requestId);
            }
            return execution.execute(request, body);
        }).build();
        HttpServiceClientOptions options = InternalCallTokenOptions.apply(
            HttpServiceClientOptions.strict().withRequestValidationFailures(), tokens, serviceId)
            .withDependencyFailureMapper((kind, cause) -> switch (kind) {
                case TIMEOUT -> new ErrorException(OperationsBffErrorCode.DEPENDENCY_TIMEOUT, null, cause);
                case UNAVAILABLE -> new ServiceUnavailableException(OperationsBffErrorCode.DEPENDENCY_UNAVAILABLE, cause);
            });
        return HttpServiceClientFactory.create(client, options, Arrays.copyOf(codes, codes.length, CodeMessageEnum[].class));
    }

    /** 连接与读取超时，禁止跟随重定向。 */
    static SimpleClientHttpRequestFactory requestFactory() {
        var factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String method) throws IOException {
                super.prepareConnection(connection, method);
                connection.setInstanceFollowRedirects(false);
            }
        };
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }

    /** 测试直连只允许 auto Profile 下的 loopback HTTP 地址。 */
    static void validateTestAddress(String value, Environment environment) {
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("审核测试直连地址格式无效");
        }
        if (!Arrays.asList(environment.getActiveProfiles()).contains("auto") || !"http".equals(uri.getScheme())
            || !"127.0.0.1".equals(uri.getHost()) || uri.getPort() < 1 || !uri.getRawPath().isEmpty()
            || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("审核测试直连只允许 auto Profile 下的 loopback HTTP 地址");
        }
    }
}
