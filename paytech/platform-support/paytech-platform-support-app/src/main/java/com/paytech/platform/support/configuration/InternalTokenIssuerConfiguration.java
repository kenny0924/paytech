package com.paytech.platform.support.configuration;

import java.time.Clock;

import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.foundation.internalauth.contract.InternalTokenIssueApi;
import com.paytech.foundation.internalauth.contract.InternalTokenIssueResponse;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditWriter;
import com.paytech.platform.support.core.configuration.InternalTokenIssuerProperties;
import com.paytech.platform.support.core.enums.InternalTokenKeyUsage;
import com.paytech.platform.support.core.internalauth.InternalTokenKeyRing;
import com.paytech.platform.support.service.processor.internalauth.IssueInternalTokenProcessor;
import com.paytech.platform.support.service.processor.internalauth.request.IssueInternalTokenRequest;

/**
 * 内部调用 Token 签发端装配（D26）；只在 {@code internal-token.issuer.enabled=true} 时生效。
 * <li>签发与拒绝都写审计：审计写入随 Operations 或 Client 人工认证启用，未启用时拒绝启动。</li>
 * <li>密钥集合同时是本服务入站校验的公钥来源；Platform 自身出站 Token 经进程内签发通道申请，同样受白名单与审计约束。</li>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "internal-token.issuer.enabled", havingValue = "true")
@EnableConfigurationProperties(InternalTokenIssuerProperties.class)
public class InternalTokenIssuerConfiguration {

    private static final String PRODUCTION_PROFILE = "prod";

    /** 无 HTTP 请求上下文时写入审计的请求 ID。 */
    private static final String UNKNOWN_REQUEST = "UNKNOWN";

    /** 签发端依赖审计写入；缺少时在启动期明确失败，而不是在首个请求失败。 */
    public InternalTokenIssuerConfiguration(ObjectProvider<OperationsAuditWriter> auditWriter) {
        if (auditWriter.getIfAvailable() == null) {
            throw new IllegalStateException("internal-token.issuer.enabled=true 需要审计写入：请同时启用 "
                + "operations.authentication.enabled 或 client.authentication.enabled");
        }
    }

    /**
     * 签发墙钟：Token 的 iat / exp 由各服务按系统 UTC 校验；不参与按类型注入，避免与业务时钟冲突。
     *
     * @return 系统 UTC 时钟
     */
    @Bean(name = IssueInternalTokenProcessor.CLOCK, defaultCandidate = false)
    Clock internalTokenClock() {
        return Clock.systemUTC();
    }

    /**
     * 校验配置并加载签名密钥；STATIC 来源只限本地与测试。
     *
     * @param properties 签发端配置
     * @param environment 环境
     * @return 密钥集合
     */
    @Bean
    InternalTokenKeyRing internalTokenKeyRing(InternalTokenIssuerProperties properties, Environment environment) {
        properties.validate();
        if (properties.getKeyUsage() == InternalTokenKeyUsage.STATIC && environment.matchesProfiles(PRODUCTION_PROFILE)) {
            throw new IllegalStateException("internal-token.issuer.key-usage=STATIC 仅限本地与测试，prod Profile 下禁止");
        }
        return new InternalTokenKeyRing(properties.getPrivateJwk(), properties.getRetiringPublicJwks());
    }

    /**
     * Platform 自身出站 Token 的进程内签发通道：以本服务名为调用方，经同一 Processor 授权、签名与审计。
     * <li>存在该 Bean 且 {@code internal-auth.mode} 非 OFF 时，Foundation 自动配置以它创建带缓存的出站 Token 来源。</li>
     *
     * @param processor 签发 Processor
     * @param environment 环境，提供本服务名
     * @return Foundation 签发合同的进程内实现
     */
    @Bean
    InternalTokenIssueApi selfInternalTokenIssueClient(IssueInternalTokenProcessor processor, Environment environment) {
        String self = environment.getRequiredProperty("spring.application.name");
        return request -> {
            String requestId = MDC.get(ApiProblemProfile.REQUEST_ID_MDC_KEY);
            var issued = processor.process(new IssueInternalTokenRequest(self, request.getTargetService(),
                request.getPurpose(), requestId == null ? UNKNOWN_REQUEST : requestId));
            InternalTokenIssueResponse response = new InternalTokenIssueResponse();
            response.setToken(issued.getToken());
            response.setExpiresAt(issued.getExpiresAt().toString());
            return response;
        };
    }
}
