package com.paytech.client.console.bff.configuration;

import com.paytech.client.contract.api.enums.ClientDraftErrorCode;
import com.paytech.client.master.api.onboarding.ClientOnboardingApplicationApi;
import com.paytech.client.master.api.onboarding.ClientOnboardingCatalogApi;
import com.paytech.client.master.api.enums.OnboardingErrorCode;
import com.paytech.foundation.common.enums.CodeMessageEnum;
import com.paytech.client.contract.api.onboarding.*;
import com.paytech.client.contract.api.workspace.ClientWorkspaceApi;
import com.paytech.foundation.common.error.*;
import com.paytech.foundation.httpclient.api.*;
import com.paytech.foundation.internalauth.api.InternalTokenSupplier;
import com.paytech.compliance.api.ClientReviewResultApi;
import com.paytech.payout.api.balance.BalanceExplanationApi;
import com.paytech.payout.api.beneficiary.BeneficiaryAccountApi;
import com.paytech.payout.api.beneficiary.BeneficiaryRequirementApi;
import com.paytech.payout.api.enums.PayoutErrorCode;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Arrays;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.loadbalancer.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.*;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/** 仅消费轻量 Owner 合同，不引入业务库、签名密钥或跨 Owner 事务。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "client.onboarding-enabled", havingValue = "true")
public class ClientOnboardingConfiguration {

    /** 多个 CCT 能力共用严格传输，实例地址不由浏览器指定。 */
    @Bean
    HttpServiceProxyFactory clientOnboardingCctFactory(Environment env, ObjectProvider<LoadBalancerClient> discovery,
        ObjectProvider<InternalTokenSupplier> tokens) {
        return HttpServiceClientFactory.create(
            transport(env, discovery, "cct", "client-master-service", ""),
            options(tokens, "client-master-service"),
            java.util.stream.Stream.concat(Arrays.stream(ClientDraftErrorCode.values()), Arrays.stream(OnboardingErrorCode.values()))
                .toArray(CodeMessageEnum[]::new)
        );
    }

    /** C04—C06 原命令和资料读取。 */
    @Bean
    ClientDraftApi clientDraftApi(HttpServiceProxyFactory clientOnboardingCctFactory) {
        return clientOnboardingCctFactory.createClient(ClientDraftApi.class);
    }

    /** CM-1 / CM-2：可入驻辖区与采集 Schema。 */
    @Bean
    ClientOnboardingCatalogApi clientOnboardingCatalogApi(HttpServiceProxyFactory clientOnboardingCctFactory) {
        return clientOnboardingCctFactory.createClient(ClientOnboardingCatalogApi.class);
    }

    /** CM-6 / CM-7 / CM-8：声明、需求清单与撤回。 */
    @Bean
    ClientOnboardingApplicationApi clientOnboardingApplicationApi(HttpServiceProxyFactory clientOnboardingCctFactory) {
        return clientOnboardingCctFactory.createClient(ClientOnboardingApplicationApi.class);
    }

    /** C14 正式提交。 */
    @Bean
    ClientSubmissionApi clientSubmissionApi(HttpServiceProxyFactory clientOnboardingCctFactory) {
        return clientOnboardingCctFactory.createClient(ClientSubmissionApi.class);
    }

    /** C01 的原申请事实。 */
    @Bean
    ClientOnboardingStartApi clientOnboardingStartApi(HttpServiceProxyFactory clientOnboardingCctFactory) {
        return clientOnboardingCctFactory.createClient(ClientOnboardingStartApi.class);
    }

    /** 工作区名称与已批准资料摘要；只返回本账号已批准的 Client。 */
    @Bean
    ClientWorkspaceApi clientWorkspaceApi(HttpServiceProxyFactory clientOnboardingCctFactory) {
        return clientOnboardingCctFactory.createClient(ClientWorkspaceApi.class);
    }

    /** C07—C11 委托唯一业务 Owner。 */
    @Bean
    ClientPeopleApi clientPeopleApi(HttpServiceProxyFactory clientOnboardingCctFactory) {
        return clientOnboardingCctFactory.createClient(ClientPeopleApi.class);
    }

    /** C12/C13 只转发，不在 BFF 绑定文件与申请。 */
    @Bean
    ClientDocumentApi clientDocumentApi(HttpServiceProxyFactory clientOnboardingCctFactory) {
        return clientOnboardingCctFactory.createClient(ClientDocumentApi.class);
    }

    /** Risk 客户可见审核结果；只在 CCT 核对申请归属后调用。 */
    @Bean
    ClientReviewResultApi clientReviewResultApi(Environment env, ObjectProvider<LoadBalancerClient> discovery,
        ObjectProvider<InternalTokenSupplier> tokens) {
        return HttpServiceClientFactory.create(
            transport(env, discovery, "risk", "compliance-service", ""),
            options(tokens, "compliance-service")
        ).createClient(ClientReviewResultApi.class);
    }

    /** payout 收款账户：列表、提交、详情、设默认与关闭；Owner 固定为当前工作区 Client。 */
    @Bean
    BeneficiaryAccountApi clientPayoutBeneficiaryAccountApi(Environment env, ObjectProvider<LoadBalancerClient> discovery,
        ObjectProvider<InternalTokenSupplier> tokens) {
        return payoutFactory(env, discovery, tokens).createClient(BeneficiaryAccountApi.class);
    }

    /** payout 收款方式 × 国家 × 币种的字段要求。 */
    @Bean
    BeneficiaryRequirementApi clientPayoutBeneficiaryRequirementApi(Environment env, ObjectProvider<LoadBalancerClient> discovery,
        ObjectProvider<InternalTokenSupplier> tokens) {
        return payoutFactory(env, discovery, tokens).createClient(BeneficiaryRequirementApi.class);
    }

    /** payout 余额解释。 */
    @Bean
    BalanceExplanationApi clientPayoutBalanceExplanationApi(Environment env, ObjectProvider<LoadBalancerClient> discovery,
        ObjectProvider<InternalTokenSupplier> tokens) {
        return payoutFactory(env, discovery, tokens).createClient(BalanceExplanationApi.class);
    }

    /**
     * payout 代理工厂：读取 4 秒（提交与余额解释在 payout 内同步调用 Vault / account），登记 PayoutErrorCode
     * <li>工厂不注册为 Bean，避免与 CCT 工厂按类型冲突；auto 测试经 client.payout-test-base-url 直连 loopback。</li>
     */
    HttpServiceProxyFactory payoutFactory(Environment env, ObjectProvider<LoadBalancerClient> discovery,
        ObjectProvider<InternalTokenSupplier> tokens) {
        return HttpServiceClientFactory.create(transport(env, discovery, "payout", "payout-service", "", Duration.ofSeconds(4)),
            options(tokens, "payout-service"), PayoutErrorCode.values());
    }

    /** 依赖失败保留 Cause，业务错误按明确合同投影；内部调用 Token 按目标服务附加（默认 OFF 不附加）。 */
    HttpServiceClientOptions options(ObjectProvider<InternalTokenSupplier> tokens, String service) {
        return InternalCallTokenOptions.apply(
            HttpServiceClientOptions.strict().withStrictJsonResponses(524288).withRequestValidationFailures(),
            tokens, service);
    }

    /** 发现失败显式不可用；auto 只允许随机 loopback 服务地址；读取超时 2 秒。 */
    RestClient transport(
        Environment env,
        ObjectProvider<LoadBalancerClient> discovery,
        String owner,
        String service,
        String context
    ) {
        return transport(env, discovery, owner, service, context, Duration.ofSeconds(2));
    }

    /** 发现失败显式不可用；auto 只允许随机 loopback 服务地址；读取超时由调用方给出。 */
    RestClient transport(
        Environment env,
        ObjectProvider<LoadBalancerClient> discovery,
        String owner,
        String service,
        String context,
        Duration readTimeout
    ) {
        String testUrl = env.getProperty("client." + owner + "-test-base-url", "");
        var builder = RestClient.builder();
        if (testUrl.isBlank()) {
            builder
                .baseUrl("http://" + service + context)
                .requestInterceptor((request, body, execution) -> {
                    var balancer = discovery.getObject();
                    var instance = balancer.choose(service);
                    if (instance == null) {
                        throw new ResourceAccessException("入驻 Owner 没有可用实例");
                    }
                    return balancer.execute(
                        service,
                        instance,
                        new LoadBalancerRequestFactory(balancer).createRequest(request, body, execution)
                    );
                });
        } else {
            var uri = URI.create(testUrl);
            if (
                !Arrays.asList(env.getActiveProfiles()).contains("auto") ||
                !"http".equals(uri.getScheme()) ||
                !"127.0.0.1".equals(uri.getHost()) ||
                uri.getPort() < 1 ||
                uri.getPort() > 65535 ||
                !context.equals(uri.getPath()) ||
                uri.getUserInfo() != null ||
                uri.getQuery() != null ||
                uri.getFragment() != null
            ) {
                throw new IllegalArgumentException("入驻测试直连仅允许 auto 的准确 loopback 根路径");
            }
            builder.baseUrl(testUrl);
        }
        var transport = new JdkClientHttpRequestFactory(
            HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(1))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()
        );
        transport.setReadTimeout(readTimeout);
        return builder.requestFactory(transport).build();
    }
}
