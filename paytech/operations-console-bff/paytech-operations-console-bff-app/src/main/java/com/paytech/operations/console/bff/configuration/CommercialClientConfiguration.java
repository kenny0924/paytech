package com.paytech.operations.console.bff.configuration;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestClient;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import com.paytech.commercial.api.agreement.AgreementApi;
import com.paytech.commercial.api.enums.CommercialPriceErrorCode;
import com.paytech.commercial.api.price.CommercialPriceApi;
import com.paytech.commercial.api.price.CommercialTermVersionPriceApi;
import com.paytech.commercial.api.pricing.PricingProfileApi;
import com.paytech.commercial.api.ratecard.RateCardApi;
import com.paytech.commercial.api.term.CommercialTermApi;
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
 * commercial 的 HTTP 接口代理：Nacos 发现 commercial-service，auto 测试改为 loopback 直连
 * <li>连接 2 秒、读取 4 秒、禁止跟随重定向、不重试；传递 X-Request-Id 与 X-Caller-Service。</li>
 * <li>严格 Problem 解码并保留 Owner 422 字段路径；合同、画像、条款与费率卡只使用公共错误码（BUSINESS_CHECK_FAILED、
 * CONCURRENT_REQUEST、NOT_FOUND）；价格命中另用一个工厂登记 CommercialPriceErrorCode（422 / 409 的命中失败原因）。
 * 超时映射 504，不可达或 503 映射 DEPENDENCY_UNAVAILABLE。</li>
 * <li>两个工厂都显式开启 Foundation 的 Owner Problem detail：业务拒绝、版本冲突、NOT_FOUND 与价格命中合同码把 commercial 已校验的
 * detail 作为本地异常消息，BFF 的 MVC 边界据此输出 Owner 原因（F9）；INVALID_REQUEST、字段校验、5xx 与协议错误不变。</li>
 * <li>工厂不注册为 Bean，避免被其他 Owner 代理按类型误用。</li>
 *
 * @author Claude
 */
@Configuration(proxyBeanMethods = false)
public class CommercialClientConfiguration {
    /** commercial 的服务发现名与内部 Token 目标。 */
    static final String COMMERCIAL_SERVICE = "commercial-service";

    /** 本 BFF 的调用方标识。 */
    static final String CALLER_SERVICE = "operations-console-bff";

    private final HttpServiceProxyFactory commercial;
    private final HttpServiceProxyFactory commercialPrices;

    /** 发现模式或 auto 测试直连地址（只允许 auto Profile 下的 loopback）。 */
    public CommercialClientConfiguration(Environment environment, LoadBalancerClient loadBalancer,
        @Value("${operations.commercial-test-base-url:}") String testBaseUrl, ObjectProvider<InternalTokenSupplier> tokens) {
        RestClient client = restClient(environment, loadBalancer, testBaseUrl);
        this.commercial = factory(client, tokens, new CodeMessageEnum[0]);
        this.commercialPrices = factory(client, tokens, CommercialPriceErrorCode.values());
    }

    /** 合同、签署、订阅与开通项。 */
    @Bean
    AgreementApi commercialAgreementApi() {
        return commercial.createClient(AgreementApi.class);
    }

    /** 定价画像。 */
    @Bean
    PricingProfileApi commercialPricingProfileApi() {
        return commercial.createClient(PricingProfileApi.class);
    }

    /** 条款方案与版本的写入和版本详情读取；操作人走 X-Gateway-Actor-Subject 请求头。 */
    @Bean
    CommercialTermApi commercialTermApi() {
        return commercial.createClient(CommercialTermApi.class);
    }

    /** 固定版本价格全集。 */
    @Bean
    CommercialTermVersionPriceApi commercialTermVersionPriceApi() {
        return commercial.createClient(CommercialTermVersionPriceApi.class);
    }

    /** 费率卡（合同详情页的当前 / 已排期版本）。 */
    @Bean
    RateCardApi commercialRateCardApi() {
        return commercial.createClient(RateCardApi.class);
    }

    /** 价格命中（运营价格试算）；422 / 409 命中失败按 CommercialPriceErrorCode 保留原因。 */
    @Bean
    CommercialPriceApi commercialPriceApi() {
        return commercialPrices.createClient(CommercialPriceApi.class);
    }

    /** 发现或直连，传递 Request ID 与调用方标识。 */
    private static RestClient restClient(Environment environment, LoadBalancerClient loadBalancer, String testBaseUrl) {
        RestClient.Builder builder = RestClient.builder();
        if (testBaseUrl.isBlank()) {
            builder.baseUrl("http://" + COMMERCIAL_SERVICE)
                .requestInterceptor(new OwnerDiscoveryInterceptor(loadBalancer, COMMERCIAL_SERVICE));
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
                request.getHeaders().set("X-Caller-Service", CALLER_SERVICE);
                return execution.execute(request, body);
            }).build();
    }

    /** 严格协议、Owner detail 外显、登记合同错误码并映射依赖故障。 */
    private static HttpServiceProxyFactory factory(RestClient client, ObjectProvider<InternalTokenSupplier> tokens,
        CodeMessageEnum[] contractErrors) {
        HttpServiceClientOptions options = InternalCallTokenOptions.apply(
            HttpServiceClientOptions.strict().withRequestValidationFailures().withOwnerProblemDetail(), tokens,
            COMMERCIAL_SERVICE)
            .withDependencyFailureMapper((kind, cause) -> switch (kind) {
                case TIMEOUT -> new ErrorException(OperationsBffErrorCode.DEPENDENCY_TIMEOUT, null, cause);
                case UNAVAILABLE -> new ServiceUnavailableException(OperationsBffErrorCode.DEPENDENCY_UNAVAILABLE, cause);
            });
        return HttpServiceClientFactory.create(client, options, contractErrors);
    }
}
