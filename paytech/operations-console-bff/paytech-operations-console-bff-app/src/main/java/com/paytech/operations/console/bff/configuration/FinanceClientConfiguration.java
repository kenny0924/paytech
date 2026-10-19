package com.paytech.operations.console.bff.configuration;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.stream.Stream;

import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import com.paytech.account.api.AccountPostingApi;
import com.paytech.account.api.BalanceQueryApi;
import com.paytech.account.api.OwnerAccountQueryApi;
import com.paytech.account.api.enums.AccountPostingErrorCode;
import com.paytech.account.api.enums.BalanceMovementErrorCode;
import com.paytech.account.api.enums.BalanceQueryErrorCode;
import com.paytech.foundation.common.enums.CodeMessageEnum;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.httpclient.api.HttpServiceClientFactory;
import com.paytech.foundation.httpclient.api.HttpServiceClientOptions;
import com.paytech.foundation.internalauth.api.InternalTokenSupplier;
import com.paytech.foundation.web.request.RequestIdFilter;
import com.paytech.ledger.api.AccountingPeriodCommandApi;
import com.paytech.ledger.api.FinanceEntityCommandApi;
import com.paytech.ledger.api.FiscalCalendarCommandApi;
import com.paytech.ledger.api.LedgerBookCommandApi;
import com.paytech.ledger.api.LedgerBookQueryApi;
import com.paytech.ledger.api.LedgerSetupQueryApi;
import com.paytech.ledger.api.enums.LedgerSetupErrorCode;
import com.paytech.operations.console.bff.domain.enums.OperationsBffErrorCode;

/**
 * ledger 与 account 的 HTTP 接口代理：两者不注册 Nacos，使用固定私网地址
 * <li>不加 LoadBalancer、不加 Discovery 拦截器、不重试；连接 2 秒、读取 4 秒，禁止跟随重定向，传递 X-Request-Id。</li>
 * <li>严格 Problem 解码并保留 Owner 422 字段路径；超时映射 504 DEPENDENCY_TIMEOUT，不可达映射 503 DEPENDENCY_UNAVAILABLE。</li>
 * <li>两个 Owner 各用独立工厂、只登记本 Owner 的合同错误码：ledger 为 LedgerSetupErrorCode；account 为 BalanceQueryErrorCode、
 * AccountPostingErrorCode 与明细流水查询使用的 BalanceMovementErrorCode.ACCOUNT_NOT_FOUND。</li>
 * <li>工厂不注册为 Bean，避免被其他 Platform/Risk 代理按类型误用。</li>
 *
 * @author Claude
 */
@Configuration(proxyBeanMethods = false)
public class FinanceClientConfiguration {
    private static final String LEDGER_SERVICE = "ledger-service";
    private static final String ACCOUNT_SERVICE = "account-service";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(4);

    private final HttpServiceProxyFactory ledger;
    private final HttpServiceProxyFactory account;

    /** 启动时校验两个固定地址并创建各自的代理工厂。 */
    public FinanceClientConfiguration(@Value("${operations.ledger-base-url}") String ledgerBaseUrl,
        @Value("${operations.account-base-url}") String accountBaseUrl, ObjectProvider<InternalTokenSupplier> tokens) {
        this.ledger = factory(validated("operations.ledger-base-url", ledgerBaseUrl), LEDGER_SERVICE, tokens,
            LedgerSetupErrorCode.values());
        this.account = factory(validated("operations.account-base-url", accountBaseUrl), ACCOUNT_SERVICE, tokens,
            accountErrorCodes());
    }

    /** ledger 账务基础资料只读查询。 */
    @Bean
    LedgerSetupQueryApi ledgerSetupQueryApi() {
        return ledger.createClient(LedgerSetupQueryApi.class);
    }

    /** ledger 财务主体命令。 */
    @Bean
    FinanceEntityCommandApi financeEntityCommandApi() {
        return ledger.createClient(FinanceEntityCommandApi.class);
    }

    /** ledger 财务日历与会计期间命令。 */
    @Bean
    FiscalCalendarCommandApi fiscalCalendarCommandApi() {
        return ledger.createClient(FiscalCalendarCommandApi.class);
    }

    /** ledger 会计期间开放命令。 */
    @Bean
    AccountingPeriodCommandApi accountingPeriodCommandApi() {
        return ledger.createClient(AccountingPeriodCommandApi.class);
    }

    /** ledger 账簿与账户命令。 */
    @Bean
    LedgerBookCommandApi ledgerBookCommandApi() {
        return ledger.createClient(LedgerBookCommandApi.class);
    }

    /** ledger 账簿、账户、流水与分录查询。 */
    @Bean
    LedgerBookQueryApi ledgerBookQueryApi() {
        return ledger.createClient(LedgerBookQueryApi.class);
    }

    /** account Owner 余额查询。 */
    @Bean
    BalanceQueryApi balanceQueryApi() {
        return account.createClient(BalanceQueryApi.class);
    }

    /** account Owner 账户与过账批次查询（明细账）。 */
    @Bean
    OwnerAccountQueryApi ownerAccountQueryApi() {
        return account.createClient(OwnerAccountQueryApi.class);
    }

    /** account 过账批次详情；BFF 只使用批次详情，不暴露进度、追平读取与按流重建。 */
    @Bean
    AccountPostingApi accountPostingApi() {
        return account.createClient(AccountPostingApi.class);
    }

    /** account 页面可能收到的合同错误码：余额无账户、批次不存在、明细流水的账户不存在。 */
    private static CodeMessageEnum[] accountErrorCodes() {
        return Stream.<CodeMessageEnum>concat(
            Stream.concat(Stream.of(BalanceQueryErrorCode.values()), Stream.of(AccountPostingErrorCode.values())),
            Stream.of(BalanceMovementErrorCode.ACCOUNT_NOT_FOUND)).toArray(CodeMessageEnum[]::new);
    }

    /** 固定地址、严格协议并映射依赖故障；只登记本 Owner 的合同错误码。 */
    private static HttpServiceProxyFactory factory(String baseUrl, String serviceId,
        ObjectProvider<InternalTokenSupplier> tokens, CodeMessageEnum[] codes) {
        RestClient client = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory())
            .requestInterceptor((request, body, execution) -> {
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
        return HttpServiceClientFactory.create(client, options, codes);
    }

    /** JDK HttpClient：连接与读取超时，禁止跟随重定向。 */
    private static JdkClientHttpRequestFactory requestFactory() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }

    /** 固定地址只允许 http(s)、带主机、无凭据、无路径、无 Query 与 Fragment。 */
    static String validated(String property, String value) {
        URI uri;
        try {
            uri = URI.create(value == null ? "" : value.trim());
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException(property + " 格式无效", invalid);
        }
        boolean valid = ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) && uri.getHost() != null
            && uri.getRawUserInfo() == null && (uri.getRawPath() == null || uri.getRawPath().isEmpty())
            && uri.getRawQuery() == null && uri.getRawFragment() == null;
        if (!valid) {
            throw new IllegalArgumentException(property + " 必须是不含路径、Query 与 Fragment 的 http(s) 地址");
        }
        return uri.toString();
    }
}
