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
import com.paytech.reconciliation.api.bank.BankMatchApi;
import com.paytech.reconciliation.api.bank.BankStatementApi;
import com.paytech.reconciliation.api.bank.ExpectedBankMovementApi;
import com.paytech.reconciliation.api.cash.CashAccountApi;
import com.paytech.reconciliation.api.clearing.ClearingAccountApi;
import com.paytech.reconciliation.api.enums.ReconciliationErrorCode;
import com.paytech.reconciliation.api.exception.ReconExceptionApi;
import com.paytech.reconciliation.api.exception.ReconResolutionApi;
import com.paytech.reconciliation.api.statement.ChannelStatementApi;
import com.paytech.reconciliation.api.statement.ChannelStatementLineApi;
import com.paytech.reconciliation.api.statement.PlatformTxnApi;
import com.paytech.reconciliation.api.summary.ReconSummaryApi;

/**
 * reconciliation 运营接口的 HTTP 代理：固定私网地址 operations.reconciliation-base-url，与 ledger / account / billing 同一接入方式
 * <li>不加 LoadBalancer、Discovery 拦截器，不重试，禁止跟随重定向；传递 X-Request-Id。连接 2 秒、读取 9 秒：渠道账单完成导入在
 * reconciliation 同步校验最多 20,000 行，读取超时须小于 Gateway 路由响应超时上限 10 秒，使 BFF 先于 Gateway 返回 504。</li>
 * <li>严格 Problem 解码并保留 Owner 422 字段路径；显式开启 Owner Problem detail，使 reconciliation 的中文业务拒绝原因（例如
 * “提交人不能复核自己的提交”）到达浏览器（AC-V6）；INVALID_REQUEST、5xx 与协议错误不外露 Owner 原文。</li>
 * <li>只登记 reconciliation-api 唯一的合同错误码 ReconciliationErrorCode；超时映射 504 DEPENDENCY_TIMEOUT，不可达映射 503。</li>
 * <li>只为运营页面需要的 API 创建代理；billing 专用的清算账户解析接口与 payout / tax 专用的当前银行账户查询不在 BFF 使用。</li>
 * <li>工厂不注册为 Bean，避免被其他 Owner 代理按类型误用。</li>
 *
 * @author Claude
 */
@Configuration(proxyBeanMethods = false)
public class ReconciliationClientConfiguration {
    private static final String RECONCILIATION_SERVICE = "reconciliation-service";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(9);

    private final HttpServiceProxyFactory reconciliation;

    /** 启动时校验固定地址并创建代理工厂。 */
    public ReconciliationClientConfiguration(
        @Value("${operations.reconciliation-base-url}") String reconciliationBaseUrl,
        ObjectProvider<InternalTokenSupplier> tokens) {
        this.reconciliation = factory(
            FinanceClientConfiguration.validated("operations.reconciliation-base-url", reconciliationBaseUrl), tokens);
    }

    /** 清算账户维护、详情与未归属 MID。 */
    @Bean
    ClearingAccountApi clearingAccountApi() {
        return reconciliation.createClient(ClearingAccountApi.class);
    }

    /** 银行账户新建、关闭申请、复核与查询。 */
    @Bean
    CashAccountApi cashAccountApi() {
        return reconciliation.createClient(CashAccountApi.class);
    }

    /** 渠道账单分段导入、作废与查询。 */
    @Bean
    ChannelStatementApi channelStatementApi() {
        return reconciliation.createClient(ChannelStatementApi.class);
    }

    /** 跨账单渠道账单行查询（渠道流水页）。 */
    @Bean
    ChannelStatementLineApi channelStatementLineApi() {
        return reconciliation.createClient(ChannelStatementLineApi.class);
    }

    /** 平台流水查询。 */
    @Bean
    PlatformTxnApi platformTxnApi() {
        return reconciliation.createClient(PlatformTxnApi.class);
    }

    /** 银行对账单分段导入与银行流水查询。 */
    @Bean
    BankStatementApi bankStatementApi() {
        return reconciliation.createClient(BankStatementApi.class);
    }

    /** 预期银行流水查询。 */
    @Bean
    ExpectedBankMovementApi expectedBankMovementApi() {
        return reconciliation.createClient(ExpectedBankMovementApi.class);
    }

    /** 银行匹配建议确认、拒绝与人工多选匹配。 */
    @Bean
    BankMatchApi bankMatchApi() {
        return reconciliation.createClient(BankMatchApi.class);
    }

    /** 对账异常人工开立、查询与处置提交。 */
    @Bean
    ReconExceptionApi reconExceptionApi() {
        return reconciliation.createClient(ReconExceptionApi.class);
    }

    /** 异常处置复核。 */
    @Bean
    ReconResolutionApi reconResolutionApi() {
        return reconciliation.createClient(ReconResolutionApi.class);
    }

    /** 在途汇总。 */
    @Bean
    ReconSummaryApi reconSummaryApi() {
        return reconciliation.createClient(ReconSummaryApi.class);
    }

    /** 固定地址、严格协议、Owner detail 外显并映射依赖故障；只登记 reconciliation 的合同错误码。 */
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
            HttpServiceClientOptions.strict().withRequestValidationFailures().withOwnerProblemDetail(), tokens,
            RECONCILIATION_SERVICE)
            .withDependencyFailureMapper((kind, cause) -> switch (kind) {
                case TIMEOUT -> new ErrorException(OperationsBffErrorCode.DEPENDENCY_TIMEOUT, null, cause);
                case UNAVAILABLE -> new ServiceUnavailableException(OperationsBffErrorCode.DEPENDENCY_UNAVAILABLE, cause);
            });
        return HttpServiceClientFactory.create(client, options, ReconciliationErrorCode.values());
    }
}
