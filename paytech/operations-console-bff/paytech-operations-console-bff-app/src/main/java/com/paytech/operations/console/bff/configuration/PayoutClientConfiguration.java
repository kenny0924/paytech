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

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.httpclient.api.HttpServiceClientFactory;
import com.paytech.foundation.httpclient.api.HttpServiceClientOptions;
import com.paytech.foundation.internalauth.api.InternalTokenSupplier;
import com.paytech.foundation.web.request.RequestIdFilter;
import com.paytech.operations.console.bff.domain.enums.OperationsBffErrorCode;
import com.paytech.operations.console.bff.integration.OwnerDiscoveryInterceptor;
import com.paytech.payout.api.balance.BalanceExplanationApi;
import com.paytech.payout.api.beneficiary.BeneficiaryAccountApi;
import com.paytech.payout.api.beneficiary.BeneficiaryRequirementApi;
import com.paytech.payout.api.beneficiary.BeneficiaryReviewApi;
import com.paytech.payout.api.enums.PayoutErrorCode;

/**
 * payout 收款账户与余额解释的 HTTP 代理：Nacos 发现 payout-service，auto 测试改为 loopback 直连
 * <li>连接 2 秒、读取 4 秒、禁止跟随重定向、不重试；传递 X-Request-Id。X-Caller-Service 由接口参数逐次传入（运营视图与 Reveal 依赖它）。</li>
 * <li>严格 Problem 解码并保留 Owner 422 字段路径；登记 PayoutErrorCode，并显式开启 Owner Problem detail，使 payout 的中文业务原因
 * （例如重复账号的已有账户 ID、四眼拒绝）到达浏览器；INVALID_REQUEST、字段校验、5xx 与协议错误不外露原文。</li>
 * <li>超时映射 504 DEPENDENCY_TIMEOUT，不可达映射 503 DEPENDENCY_UNAVAILABLE；工厂不注册为 Bean。</li>
 *
 * @author Claude
 */
@Configuration(proxyBeanMethods = false)
public class PayoutClientConfiguration {
    /** payout 的服务发现名与内部 Token 目标。 */
    static final String PAYOUT_SERVICE = "payout-service";

    private final HttpServiceProxyFactory payout;

    /** 发现模式或 auto 测试直连地址（只允许 auto Profile 下的 loopback）。 */
    public PayoutClientConfiguration(Environment environment, LoadBalancerClient loadBalancer,
        @Value("${operations.payout-test-base-url:}") String testBaseUrl, ObjectProvider<InternalTokenSupplier> tokens) {
        this.payout = factory(restClient(environment, loadBalancer, testBaseUrl), tokens);
    }

    /** 收款账户提交、核验、状态命令、原值查看与查询。 */
    @Bean
    BeneficiaryAccountApi payoutBeneficiaryAccountApi() {
        return payout.createClient(BeneficiaryAccountApi.class);
    }

    /** 核验队列。 */
    @Bean
    BeneficiaryReviewApi payoutBeneficiaryReviewApi() {
        return payout.createClient(BeneficiaryReviewApi.class);
    }

    /** 收款方式 × 国家 × 币种的字段要求（代提交表单）。 */
    @Bean
    BeneficiaryRequirementApi payoutBeneficiaryRequirementApi() {
        return payout.createClient(BeneficiaryRequirementApi.class);
    }

    /** Owner 余额解释。 */
    @Bean
    BalanceExplanationApi payoutBalanceExplanationApi() {
        return payout.createClient(BalanceExplanationApi.class);
    }

    /** 发现或直连，传递 Request ID。 */
    private static RestClient restClient(Environment environment, LoadBalancerClient loadBalancer, String testBaseUrl) {
        RestClient.Builder builder = RestClient.builder();
        if (testBaseUrl.isBlank()) {
            builder.baseUrl("http://" + PAYOUT_SERVICE)
                .requestInterceptor(new OwnerDiscoveryInterceptor(loadBalancer, PAYOUT_SERVICE));
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

    /** 严格协议、Owner detail 外显、登记 payout 合同错误码并映射依赖故障。 */
    private static HttpServiceProxyFactory factory(RestClient client, ObjectProvider<InternalTokenSupplier> tokens) {
        HttpServiceClientOptions options = InternalCallTokenOptions.apply(
            HttpServiceClientOptions.strict().withRequestValidationFailures().withOwnerProblemDetail(), tokens,
            PAYOUT_SERVICE)
            .withDependencyFailureMapper((kind, cause) -> switch (kind) {
                case TIMEOUT -> new ErrorException(OperationsBffErrorCode.DEPENDENCY_TIMEOUT, null, cause);
                case UNAVAILABLE -> new ServiceUnavailableException(OperationsBffErrorCode.DEPENDENCY_UNAVAILABLE, cause);
            });
        return HttpServiceClientFactory.create(client, options, PayoutErrorCode.values());
    }
}
