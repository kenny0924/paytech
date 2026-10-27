package com.paytech.operations.console.bff.configuration;

import java.util.stream.Stream;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import com.paytech.client.master.api.entityadmin.ClientMasterEntityAdminApi;
import com.paytech.client.master.api.enums.CheckoutCandidateErrorCode;
import com.paytech.client.master.api.enums.EntityAdminErrorCode;
import com.paytech.client.master.api.enums.OnboardingErrorCode;
import com.paytech.client.master.api.onboardingadmin.ClientOnboardingAdminApi;
import com.paytech.compliance.api.QualificationAdminApi;
import com.paytech.compliance.api.enums.QualificationErrorCode;
import com.paytech.foundation.common.enums.CodeMessageEnum;
import com.paytech.foundation.internalauth.api.InternalTokenSupplier;

/**
 * 主档维护与入驻管理（client-master）、资格登记（compliance）的 Owner 代理
 * <li>沿用 {@link ClientReviewClientConfiguration#factory} 的发现、超时、禁止重定向、Request ID 传递与依赖故障映射；不重试。</li>
 * <li>auto 测试复用入驻审核的直连地址属性：client-master 用 operations.cct-test-base-url，compliance 用
 * operations.client-review-risk-test-base-url。</li>
 * <li>client-master 登记主档错误码与 Client 摘要复用的 CLIENT_NOT_FOUND；compliance 登记资格错误码；其他 4xx 按上游响应无效处理。</li>
 */
@Configuration(proxyBeanMethods = false)
public class EntityQualificationClientConfiguration {

    /** client-master 经营主体、Local Partner 与 Client 摘要。 */
    @Bean
    ClientMasterEntityAdminApi clientMasterEntityAdminApi(Environment environment, LoadBalancerClient loadBalancer,
        @Value("${operations.cct-test-base-url:}") String testBaseUrl, ObjectProvider<InternalTokenSupplier> tokens) {
        CodeMessageEnum[] codes = Stream.concat(Stream.of(EntityAdminErrorCode.values()),
            Stream.of(CheckoutCandidateErrorCode.CLIENT_NOT_FOUND)).toArray(CodeMessageEnum[]::new);
        return ClientReviewClientConfiguration.factory(environment, loadBalancer, "client-master-service", testBaseUrl, tokens, codes)
            .createClient(ClientMasterEntityAdminApi.class);
    }

    /** client-master 入驻配置、Client 列表 / 详情与入驻码（CM-10/11/12）；登记入驻错误码与 CLIENT_NOT_FOUND。 */
    @Bean
    ClientOnboardingAdminApi clientOnboardingAdminApi(Environment environment, LoadBalancerClient loadBalancer,
        @Value("${operations.cct-test-base-url:}") String testBaseUrl, ObjectProvider<InternalTokenSupplier> tokens) {
        CodeMessageEnum[] codes = Stream.concat(Stream.of(OnboardingErrorCode.values()),
            Stream.of(CheckoutCandidateErrorCode.CLIENT_NOT_FOUND)).toArray(CodeMessageEnum[]::new);
        return ClientReviewClientConfiguration.factory(environment, loadBalancer, "client-master-service", testBaseUrl, tokens, codes)
            .createClient(ClientOnboardingAdminApi.class);
    }

    /** compliance 角色资格查询、历史、登记与撤销。 */
    @Bean
    QualificationAdminApi qualificationAdminApi(Environment environment, LoadBalancerClient loadBalancer,
        @Value("${operations.client-review-risk-test-base-url:}") String testBaseUrl, ObjectProvider<InternalTokenSupplier> tokens) {
        return ClientReviewClientConfiguration.factory(environment, loadBalancer, "compliance-service", testBaseUrl, tokens,
            QualificationErrorCode.values()).createClient(QualificationAdminApi.class);
    }
}
