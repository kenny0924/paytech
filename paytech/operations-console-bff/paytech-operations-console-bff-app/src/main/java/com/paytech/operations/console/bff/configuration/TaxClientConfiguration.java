package com.paytech.operations.console.bff.configuration;

import java.net.http.HttpClient;
import java.time.Duration;

import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.httpclient.api.HttpServiceClientFactory;
import com.paytech.foundation.httpclient.api.HttpServiceClientOptions;
import com.paytech.foundation.internalauth.api.InternalTokenSupplier;
import com.paytech.foundation.web.request.RequestIdFilter;
import com.paytech.operations.console.bff.domain.enums.OperationsBffErrorCode;
import com.paytech.tax.api.TaxOrderQueryApi;
import com.paytech.tax.api.enums.TaxErrorCode;

/**
 * tax 订单税务查询的 HTTP 代理：tax 不注册 Nacos，使用固定私网地址 operations.tax-base-url，与 billing / reconciliation 同一接入方式
 * <li>不加 LoadBalancer、Discovery 拦截器，不重试，禁止跟随重定向；传递 X-Request-Id。连接 2 秒、读取 4 秒。</li>
 * <li>严格 Problem 解码；只登记 tax-api 的合同错误码 TaxErrorCode（页面据 TAX_ORDER_NOT_FOUND 显示"订单未找到"），超时映射 504
 * DEPENDENCY_TIMEOUT，不可达映射 503 DEPENDENCY_UNAVAILABLE。</li>
 * <li>只为税务查询页创建 TaxOrderQueryApi 代理；计算接口归 acquiring 使用，BFF 不调用。工厂不注册为 Bean。</li>
 */
@Configuration(proxyBeanMethods = false)
public class TaxClientConfiguration {
    private static final String TAX_SERVICE = "tax-service";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(4);

    private final HttpServiceProxyFactory tax;

    /** 启动时校验固定地址并创建代理工厂。 */
    public TaxClientConfiguration(@Value("${operations.tax-base-url}") String taxBaseUrl,
        ObjectProvider<InternalTokenSupplier> tokens) {
        this.tax = factory(FinanceClientConfiguration.validated("operations.tax-base-url", taxBaseUrl), tokens);
    }

    /** tax 按订单号查询计算、正式交易、底稿与单据。 */
    @Bean
    TaxOrderQueryApi taxOrderQueryApi() {
        return tax.createClient(TaxOrderQueryApi.class);
    }

    /** 固定地址、严格协议并映射依赖故障；只登记 tax 的合同错误码。 */
    private static HttpServiceProxyFactory factory(String baseUrl, ObjectProvider<InternalTokenSupplier> tokens) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER).build();
        var requestFactory = new JdkClientHttpRequestFactory(http);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        RestClient client = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory)
            .requestInterceptor((request, body, execution) -> {
                String requestId = MDC.get(RequestIdFilter.MDC_KEY);
                if (requestId != null) {
                    request.getHeaders().set(RequestIdFilter.HEADER_NAME, requestId);
                }
                return execution.execute(request, body);
            }).build();
        HttpServiceClientOptions options = InternalCallTokenOptions.apply(
            HttpServiceClientOptions.strict().withRequestValidationFailures(), tokens, TAX_SERVICE)
            .withDependencyFailureMapper((kind, cause) -> switch (kind) {
                case TIMEOUT -> new ErrorException(OperationsBffErrorCode.DEPENDENCY_TIMEOUT, null, cause);
                case UNAVAILABLE -> new ServiceUnavailableException(OperationsBffErrorCode.DEPENDENCY_UNAVAILABLE, cause);
            });
        return HttpServiceClientFactory.create(client, options, TaxErrorCode.values());
    }
}
