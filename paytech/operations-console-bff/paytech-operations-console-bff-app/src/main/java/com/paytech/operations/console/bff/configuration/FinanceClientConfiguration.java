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
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import com.paytech.account.api.AccountPostingApi;
import com.paytech.account.api.BalanceQueryApi;
import com.paytech.account.api.OwnerAccountQueryApi;
import com.paytech.account.api.enums.AccountPostingErrorCode;
import com.paytech.account.api.enums.BalanceMovementErrorCode;
import com.paytech.account.api.enums.BalanceQueryErrorCode;
import com.paytech.billing.api.BillingFactCommandApi;
import com.paytech.billing.api.BillingFactQueryApi;
import com.paytech.billing.api.enums.BillingFactErrorCode;
import com.paytech.foundation.common.enums.CodeMessageEnum;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.httpclient.api.HttpServiceClientFactory;
import com.paytech.foundation.httpclient.api.HttpServiceClientOptions;
import com.paytech.foundation.internalauth.api.InternalTokenSupplier;
import com.paytech.foundation.web.request.RequestIdFilter;
import com.paytech.ledger.api.AccountingPeriodCommandApi;
import com.paytech.ledger.api.AccountingRuleBundleApi;
import com.paytech.ledger.api.ChartOfAccountsCommandApi;
import com.paytech.ledger.api.FinanceEntityCommandApi;
import com.paytech.ledger.api.FiscalCalendarCommandApi;
import com.paytech.ledger.api.LedgerBookCommandApi;
import com.paytech.ledger.api.LedgerBookQueryApi;
import com.paytech.ledger.api.LedgerSetupQueryApi;
import com.paytech.ledger.api.ManualAdjustmentApi;
import com.paytech.ledger.api.PeriodCloseApi;
import com.paytech.ledger.api.PostingProgressQueryApi;
import com.paytech.ledger.api.PostingStreamCommandApi;
import com.paytech.ledger.api.TrialBalanceApi;
import com.paytech.ledger.api.enums.LedgerErrorCode;
import com.paytech.ledger.api.enums.LedgerSetupErrorCode;
import com.paytech.operations.console.bff.domain.enums.OperationsBffErrorCode;

/**
 * ledger、account 与 billing 的 HTTP 接口代理：三者不注册 Nacos，使用固定私网地址
 * <li>不加 LoadBalancer、不加 Discovery 拦截器、不重试；连接 2 秒、读取 4 秒，禁止跟随重定向，传递 X-Request-Id。</li>
 * <li>关账发起、重开发起与关账/重开审批在 ledger 同步执行（含损益结转与冲回），单独使用一个读取超时更长的 ledger 工厂
 * （operations.ledger-long-running-read-timeout，默认 9 秒，须小于 Gateway 路由响应超时上限 10 秒）；其他调用不受影响。</li>
 * <li>明细账运维（2026-10-30）：账簿入账进度查询走上述长耗时 ledger 工厂（ledger 内部还要读 billing 与 account）；ledger 追平与 account
 * 余额重建各用一个专用工厂：读取超时分别为 operations.ledger-long-running-read-timeout 与 operations.account-long-running-read-timeout
 * （默认 9 秒），并显式外显 Owner Problem detail，使规则拒绝、无 OPEN 期间、并发冲突等原因原样到达页面；其他 ledger / account 调用不受影响。</li>
 * <li>严格 Problem 解码并保留 Owner 422 字段路径；超时映射 504 DEPENDENCY_TIMEOUT，不可达映射 503 DEPENDENCY_UNAVAILABLE。</li>
 * <li>每个 Owner 各用独立工厂、只登记本 Owner 的合同错误码：ledger 为 LedgerSetupErrorCode 与 LedgerErrorCode；account 为
 * BalanceQueryErrorCode、AccountPostingErrorCode 与明细流水查询使用的 BalanceMovementErrorCode.ACCOUNT_NOT_FOUND；billing 为
 * BillingFactErrorCode。未登记的业务拒绝为公共 BUSINESS_CHECK_FAILED。</li>
 * <li>工厂不注册为 Bean，避免被其他 Platform/Risk 代理按类型误用。</li>
 *
 * @author Claude
 */
@Configuration(proxyBeanMethods = false)
public class FinanceClientConfiguration {
    private static final String LEDGER_SERVICE = "ledger-service";
    private static final String ACCOUNT_SERVICE = "account-service";
    private static final String BILLING_SERVICE = "billing-service";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(4);
    private static final Duration GATEWAY_RESPONSE_CEILING = Duration.ofSeconds(10);

    private final HttpServiceProxyFactory ledger;
    private final HttpServiceProxyFactory longRunningLedger;
    private final HttpServiceProxyFactory postingOpsLedger;
    private final HttpServiceProxyFactory account;
    private final HttpServiceProxyFactory postingOpsAccount;
    private final HttpServiceProxyFactory billing;

    /** 启动时校验三个固定地址与长耗时读取超时，并创建各自的代理工厂。 */
    public FinanceClientConfiguration(@Value("${operations.ledger-base-url}") String ledgerBaseUrl,
        @Value("${operations.account-base-url}") String accountBaseUrl,
        @Value("${operations.billing-base-url}") String billingBaseUrl,
        @Value("${operations.ledger-long-running-read-timeout}") Duration longRunningReadTimeout,
        @Value("${operations.account-long-running-read-timeout}") Duration accountLongRunningReadTimeout,
        ObjectProvider<InternalTokenSupplier> tokens) {
        String ledgerUrl = validated("operations.ledger-base-url", ledgerBaseUrl);
        Duration ledgerLongRunning = validatedLongRunning("operations.ledger-long-running-read-timeout",
            longRunningReadTimeout);
        this.ledger = factory(ledgerUrl, LEDGER_SERVICE, tokens, READ_TIMEOUT, false, ledgerErrorCodes());
        this.longRunningLedger = factory(ledgerUrl, LEDGER_SERVICE, tokens, ledgerLongRunning, false,
            ledgerErrorCodes());
        this.postingOpsLedger = factory(ledgerUrl, LEDGER_SERVICE, tokens, ledgerLongRunning, true, ledgerErrorCodes());
        String accountUrl = validated("operations.account-base-url", accountBaseUrl);
        this.account = factory(accountUrl, ACCOUNT_SERVICE, tokens, READ_TIMEOUT, false, accountErrorCodes());
        this.postingOpsAccount = factory(accountUrl, ACCOUNT_SERVICE, tokens,
            validatedLongRunning("operations.account-long-running-read-timeout", accountLongRunningReadTimeout), true,
            accountErrorCodes());
        this.billing = factory(validated("operations.billing-base-url", billingBaseUrl), BILLING_SERVICE, tokens,
            READ_TIMEOUT, false, BillingFactErrorCode.values());
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

    /** ledger 科目维护命令。 */
    @Bean
    ChartOfAccountsCommandApi chartOfAccountsCommandApi() {
        return ledger.createClient(ChartOfAccountsCommandApi.class);
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

    /** ledger 科目余额、试算记录查询与发起试算。 */
    @Bean
    TrialBalanceApi trialBalanceApi() {
        return ledger.createClient(TrialBalanceApi.class);
    }

    /** ledger 人工调整列表、详情与 Maker-Checker 命令。 */
    @Bean
    ManualAdjustmentApi manualAdjustmentApi() {
        return ledger.createClient(ManualAdjustmentApi.class);
    }

    /** ledger 期间关账列表、详情与驳回（普通读取超时）。 */
    @Bean
    @Primary
    PeriodCloseApi periodCloseApi() {
        return ledger.createClient(PeriodCloseApi.class);
    }

    /** ledger 关账发起、重开发起与审批：同步执行关账步骤，使用长耗时读取超时。 */
    @Bean
    PeriodCloseApi longRunningPeriodCloseApi() {
        return longRunningLedger.createClient(PeriodCloseApi.class);
    }

    /** ledger 账簿入账进度（明细账运维）：ledger 内部还要读 billing 与 account，使用长耗时读取超时。 */
    @Bean
    PostingProgressQueryApi postingProgressQueryApi() {
        return longRunningLedger.createClient(PostingProgressQueryApi.class);
    }

    /** ledger 过账流追平（明细账运维）：长耗时读取超时，外显 Owner detail。 */
    @Bean
    PostingStreamCommandApi postingStreamCommandApi() {
        return postingOpsLedger.createClient(PostingStreamCommandApi.class);
    }

    /** ledger 记账规则包与规则版本。 */
    @Bean
    AccountingRuleBundleApi accountingRuleBundleApi() {
        return ledger.createClient(AccountingRuleBundleApi.class);
    }

    /** billing 计费事实登记、批准与作废。 */
    @Bean
    BillingFactCommandApi billingFactCommandApi() {
        return billing.createClient(BillingFactCommandApi.class);
    }

    /** billing 计费事实管理列表与详情；BFF 不使用重放、进度与按引用读取。 */
    @Bean
    BillingFactQueryApi billingFactQueryApi() {
        return billing.createClient(BillingFactQueryApi.class);
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

    /** account 过账批次详情与按流批次列表（普通读取超时）；BFF 不使用 account 进度。 */
    @Bean
    @Primary
    AccountPostingApi accountPostingApi() {
        return account.createClient(AccountPostingApi.class);
    }

    /** account 按流重建余额（明细账运维）：逐流锁行重算，使用长耗时读取超时，外显 Owner detail。 */
    @Bean
    AccountPostingApi longRunningAccountPostingApi() {
        return postingOpsAccount.createClient(AccountPostingApi.class);
    }

    /** ledger 页面可能收到的合同错误码：基础资料与账本的 LedgerSetupErrorCode；人工调整、关账与规则包的 LedgerErrorCode。 */
    private static CodeMessageEnum[] ledgerErrorCodes() {
        return Stream.<CodeMessageEnum>concat(Stream.of(LedgerSetupErrorCode.values()), Stream.of(LedgerErrorCode.values()))
            .toArray(CodeMessageEnum[]::new);
    }

    /** 长耗时读取超时不得小于普通读取超时，且小于 Gateway 路由响应超时上限 10 秒，使 BFF 先于 Gateway 返回 504。 */
    static Duration validatedLongRunning(String property, Duration value) {
        if (value == null || value.compareTo(READ_TIMEOUT) < 0 || value.compareTo(GATEWAY_RESPONSE_CEILING) >= 0) {
            throw new IllegalArgumentException(property + " 必须不小于 4 秒且小于 10 秒");
        }
        return value;
    }

    /** account 页面可能收到的合同错误码：余额无账户、批次不存在、明细流水的账户不存在。 */
    private static CodeMessageEnum[] accountErrorCodes() {
        return Stream.<CodeMessageEnum>concat(
            Stream.concat(Stream.of(BalanceQueryErrorCode.values()), Stream.of(AccountPostingErrorCode.values())),
            Stream.of(BalanceMovementErrorCode.ACCOUNT_NOT_FOUND)).toArray(CodeMessageEnum[]::new);
    }

    /** 固定地址、严格协议并映射依赖故障；只登记本 Owner 的合同错误码，按需外显 Owner Problem detail。 */
    private static HttpServiceProxyFactory factory(String baseUrl, String serviceId,
        ObjectProvider<InternalTokenSupplier> tokens, Duration readTimeout, boolean ownerProblemDetail,
        CodeMessageEnum... codes) {
        RestClient client = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory(readTimeout))
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
        return HttpServiceClientFactory.create(client, ownerProblemDetail ? options.withOwnerProblemDetail() : options,
            codes);
    }

    /** JDK HttpClient：连接与读取超时，禁止跟随重定向。 */
    private static JdkClientHttpRequestFactory requestFactory(Duration readTimeout) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(readTimeout);
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
