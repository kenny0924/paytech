package com.paytech.client.console.bff.configuration;

import com.paytech.client.contract.api.enums.ClientDraftErrorCode;
import com.paytech.client.contract.api.onboarding.*;
import com.paytech.client.contract.api.workspace.ClientWorkspaceApi;
import com.paytech.foundation.common.error.*;
import com.paytech.foundation.httpclient.api.*;
import com.paytech.foundation.internalauth.api.InternalTokenSupplier;
import com.paytech.platform.support.api.enums.*;
import com.paytech.platform.support.api.onboardingfiles.OnboardingFileApi;
import com.paytech.compliance.api.MaterialRequirementsApi;
import com.paytech.compliance.api.ClientReviewResultApi;
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
            ClientDraftErrorCode.values()
        );
    }

    /** C04—C06 原命令和资料读取。 */
    @Bean
    ClientDraftApi clientDraftApi(HttpServiceProxyFactory clientOnboardingCctFactory) {
        return clientOnboardingCctFactory.createClient(ClientDraftApi.class);
    }

    /** C14 正式提交。 */
    @Bean
    ClientSubmissionApi clientSubmissionApi(HttpServiceProxyFactory clientOnboardingCctFactory) {
        return clientOnboardingCctFactory.createClient(ClientSubmissionApi.class);
    }

    /** C03 的 CCT 受控目录，使用同一严格私网传输。 */
    @Bean
    ClientFormOptionsApi clientFormOptionsApi(HttpServiceProxyFactory clientOnboardingCctFactory) {
        return clientOnboardingCctFactory.createClient(ClientFormOptionsApi.class);
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

    /** F01—F04 原申请文件合同，不请求 Platform 私网审核验证。 */
    @Bean
    OnboardingFileApi onboardingFileApi(Environment env, ObjectProvider<LoadBalancerClient> discovery,
        ObjectProvider<InternalTokenSupplier> tokens) {
        return HttpServiceClientFactory.create(
            transport(env, discovery, "platform", "platform-support-service", "/platform-support"),
            options(tokens, "platform-support-service"),
            java.util.stream.Stream.concat(
                Arrays.stream(OnboardingFileErrorCode.values()),
                Arrays.stream(ClientAuthenticationErrorCode.values())
            ).toArray(com.paytech.foundation.common.enums.CodeMessageEnum[]::new)
        ).createClient(OnboardingFileApi.class);
    }

    /** Risk 的非个人材料目录仅供授权页面查询。 */
    @Bean
    MaterialRequirementsApi materialRequirementsApi(Environment env, ObjectProvider<LoadBalancerClient> discovery,
        ObjectProvider<InternalTokenSupplier> tokens) {
        return HttpServiceClientFactory.create(
            transport(env, discovery, "risk", "compliance-service", ""),
            options(tokens, "compliance-service")
        ).createClient(MaterialRequirementsApi.class);
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

    /** 依赖失败保留 Cause，业务错误按明确合同投影；内部调用 Token 按目标服务附加（默认 OFF 不附加）。 */
    HttpServiceClientOptions options(ObjectProvider<InternalTokenSupplier> tokens, String service) {
        return InternalCallTokenOptions.apply(
            HttpServiceClientOptions.strict().withStrictJsonResponses(524288).withRequestValidationFailures(),
            tokens, service);
    }

    /** 发现失败显式不可用；auto 只允许随机 loopback 服务地址。 */
    RestClient transport(
        Environment env,
        ObjectProvider<LoadBalancerClient> discovery,
        String owner,
        String service,
        String context
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
        transport.setReadTimeout(Duration.ofSeconds(2));
        return builder.requestFactory(transport).build();
    }
}
