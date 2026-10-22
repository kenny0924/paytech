package com.paytech.operations.console.bff.configuration;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestClient;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import com.paytech.card.api.CardApiConstants;
import com.paytech.card.api.binlibrary.CardBinLibraryApi;
import com.paytech.foundation.common.enums.CodeMessageEnum;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.httpclient.api.HttpServiceClientFactory;
import com.paytech.foundation.httpclient.api.HttpServiceClientOptions;
import com.paytech.foundation.internalauth.api.InternalTokenSupplier;
import com.paytech.foundation.web.request.RequestIdFilter;
import com.paytech.operations.console.bff.domain.enums.OperationsBffErrorCode;
import com.paytech.operations.console.bff.integration.OwnerDiscoveryInterceptor;

import org.slf4j.MDC;

/**
 * card-service 的 HTTP 接口代理：Nacos 发现 card-service（无 context path），auto 测试改为 loopback 直连
 * <li>连接 2 秒、读取 4 秒、禁止跟随重定向、不重试；传递 X-Request-Id。调用方 X-Caller-Service 由 Card 合同的显式 Header 参数携带。</li>
 * <li>严格 Problem 解码并保留 Owner 422 字段路径；Card BIN 库只使用公共错误码（technique.md 3.5.1 不新增专有错误码），因此不登记 Owner
 * 错误码。开启 Owner Problem detail：业务拒绝（文件来源 / 类型 / 扫描不符、状态不允许激活）、NOT_FOUND 与 409 的原因到达页面。
 * 超时映射 504，不可达或 503 映射 DEPENDENCY_UNAVAILABLE。</li>
 *
 * @author Claude
 */
@Configuration(proxyBeanMethods = false)
public class CardClientConfiguration {
    private final HttpServiceProxyFactory card;

    /** 发现模式或 auto 测试直连地址（只允许 auto Profile 下的 loopback）。 */
    public CardClientConfiguration(Environment environment, LoadBalancerClient loadBalancer,
        @Value("${operations.card-test-base-url:}") String testBaseUrl, ObjectProvider<InternalTokenSupplier> tokens) {
        this.card = factory(restClient(environment, loadBalancer, testBaseUrl), tokens);
    }

    /** BIN 库版本、导入错误、激活与 BIN 查询。 */
    @Bean
    CardBinLibraryApi cardBinLibraryApi() {
        return card.createClient(CardBinLibraryApi.class);
    }

    /** 发现或直连，传递 Request ID。 */
    private static RestClient restClient(Environment environment, LoadBalancerClient loadBalancer, String testBaseUrl) {
        RestClient.Builder builder = RestClient.builder();
        if (testBaseUrl.isBlank()) {
            builder.baseUrl("http://" + CardApiConstants.SERVICE_ID)
                .requestInterceptor(new OwnerDiscoveryInterceptor(loadBalancer, CardApiConstants.SERVICE_ID));
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
                return execution.execute(request, body);
            }).build();
    }

    /** 严格协议、Owner detail 外显并映射依赖故障。 */
    private static HttpServiceProxyFactory factory(RestClient client, ObjectProvider<InternalTokenSupplier> tokens) {
        HttpServiceClientOptions options = InternalCallTokenOptions.apply(
            HttpServiceClientOptions.strict().withRequestValidationFailures().withOwnerProblemDetail(), tokens,
            CardApiConstants.SERVICE_ID)
            .withDependencyFailureMapper((kind, cause) -> switch (kind) {
                case TIMEOUT -> new ErrorException(OperationsBffErrorCode.DEPENDENCY_TIMEOUT, null, cause);
                case UNAVAILABLE -> new ServiceUnavailableException(OperationsBffErrorCode.DEPENDENCY_UNAVAILABLE, cause);
            });
        return HttpServiceClientFactory.create(client, options, new CodeMessageEnum[0]);
    }
}
