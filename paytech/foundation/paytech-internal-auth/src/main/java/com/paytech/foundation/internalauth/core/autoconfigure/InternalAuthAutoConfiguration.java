package com.paytech.foundation.internalauth.core.autoconfigure;

import java.text.ParseException;
import java.time.Clock;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

import com.nimbusds.jose.jwk.ECKey;
import com.paytech.foundation.internalauth.api.InternalAuthMode;
import com.paytech.foundation.internalauth.api.InternalTokenKeySource;
import com.paytech.foundation.internalauth.api.InternalTokenSupplier;
import com.paytech.foundation.internalauth.api.InternalTokenVerifier;
import com.paytech.foundation.internalauth.contract.InternalTokenIssueApi;
import com.paytech.foundation.internalauth.core.inbound.InternalCallerAuthenticator;
import com.paytech.foundation.internalauth.core.key.JwksInternalTokenKeySource;
import com.paytech.foundation.internalauth.core.supplier.PlatformInternalTokenSupplier;
import com.paytech.foundation.internalauth.core.supplier.StaticKeyInternalTokenSupport;
import com.paytech.foundation.internalauth.core.token.InternalTokenVerifierImpl;

/**
 * 内部调用身份自动配置；{@code internal-auth.mode=OFF}（默认）时不创建任何 Bean。
 * <li>公钥来源：配置了 {@code internal-auth.static-key.*} 时用本地密钥对（仅限本地，prod Profile 拒绝），否则用
 * {@code internal-auth.jwks-uri}。</li>
 * <li>出站 Token：本地密钥对，或宿主提供 {@link InternalTokenIssueApi} Bean 时向 Platform 申请。</li>
 *
 * @author Claude
 */
@AutoConfiguration
@Conditional(InternalAuthAutoConfiguration.ModeEnabledCondition.class)
@EnableConfigurationProperties(InternalAuthProperties.class)
public class InternalAuthAutoConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(InternalAuthAutoConfiguration.class);

    private static final String PRODUCTION_PROFILE = "prod";

    /**
     * 本地静态密钥对；只在配置了 static-key 时创建。
     *
     * @param properties 配置
     * @param environment 环境
     * @return 本地签发与公钥来源
     */
    @Bean
    @ConditionalOnMissingBean
    @Conditional(StaticKeyConfiguredCondition.class)
    public StaticKeyInternalTokenSupport staticKeyInternalTokenSupport(InternalAuthProperties properties,
        Environment environment) {
        if (environment.matchesProfiles(PRODUCTION_PROFILE)) {
            throw new IllegalStateException("internal-auth.static-key 仅限本地与测试，prod Profile 下禁止配置");
        }
        InternalAuthProperties.StaticKey staticKey = properties.getStaticKey();
        if (staticKey.getPrivateJwk() == null || staticKey.getPublicJwk() == null) {
            throw new IllegalStateException("internal-auth.static-key.private-jwk 与 public-jwk 必须同时配置");
        }
        LOGGER.warn("internal-auth 使用本地静态密钥对签发与校验内部调用 Token，仅限本地与测试环境");
        return new StaticKeyInternalTokenSupport(parse(staticKey.getPrivateJwk(), "private-jwk"),
            parse(staticKey.getPublicJwk(), "public-jwk"), requiredApplicationName(environment),
            requiredInstanceId(environment), Clock.systemUTC());
    }

    /**
     * 校验公钥来源。
     *
     * @param properties 配置
     * @param staticKey 本地密钥对
     * @return 公钥来源
     */
    @Bean
    @ConditionalOnMissingBean
    public InternalTokenKeySource internalTokenKeySource(InternalAuthProperties properties,
        ObjectProvider<StaticKeyInternalTokenSupport> staticKey) {
        StaticKeyInternalTokenSupport local = staticKey.getIfAvailable();
        if (local != null) {
            return local;
        }
        if (properties.getJwksUri() == null) {
            throw new IllegalStateException("internal-auth.mode 非 OFF 时必须配置 internal-auth.jwks-uri 或 static-key");
        }
        return new JwksInternalTokenKeySource(properties.getJwksUri(), properties.getJwksCacheTtl(),
            properties.getJwksTimeout(), properties.getJwksForcedRefreshInterval(), Clock.systemUTC());
    }

    /**
     * Token 校验器：{@code aud} 为本服务名，{@code inst} 为本实例。
     *
     * @param keySource 公钥来源
     * @param environment 环境
     * @return 校验器
     */
    @Bean
    @ConditionalOnMissingBean
    public InternalTokenVerifier internalTokenVerifier(InternalTokenKeySource keySource, Environment environment) {
        return new InternalTokenVerifierImpl(keySource, requiredApplicationName(environment),
            requiredInstanceId(environment), Clock.systemUTC());
    }

    /**
     * 入站判定器，供 Servlet 与 WebFlux 过滤器使用。
     *
     * @param properties 配置
     * @param verifier 校验器
     * @return 判定器
     */
    @Bean
    @ConditionalOnMissingBean
    public InternalCallerAuthenticator internalCallerAuthenticator(InternalAuthProperties properties,
        InternalTokenVerifier verifier) {
        return new InternalCallerAuthenticator(properties.getMode(), verifier, properties.getExemptPathPrefixes());
    }

    /**
     * 出站 Token 来源：本地密钥对优先，否则使用宿主提供的 Platform 签发客户端；两者都没有时不创建。
     *
     * @param staticKey 本地密钥对
     * @param issueApi Platform 签发客户端
     * @return Token 来源
     */
    @Bean
    @ConditionalOnMissingBean
    @Conditional(SupplierSourceCondition.class)
    public InternalTokenSupplier internalTokenSupplier(ObjectProvider<StaticKeyInternalTokenSupport> staticKey,
        ObjectProvider<InternalTokenIssueApi> issueApi) {
        StaticKeyInternalTokenSupport local = staticKey.getIfAvailable();
        if (local != null) {
            return local;
        }
        return new PlatformInternalTokenSupplier(issueApi.getObject(), Clock.systemUTC());
    }

    private static ECKey parse(String json, String name) {
        try {
            return ECKey.parse(json);
        } catch (ParseException exception) {
            throw new IllegalStateException("internal-auth.static-key." + name + " 不是合法的 EC JWK", exception);
        }
    }

    private static String requiredApplicationName(Environment environment) {
        String name = environment.getProperty("spring.application.name");
        if (name == null || name.isBlank()) {
            throw new IllegalStateException("internal-auth 需要 spring.application.name 作为本服务名");
        }
        return name;
    }

    private static String requiredInstanceId(Environment environment) {
        String instanceId = environment.getProperty("paytech.instance-id");
        if (instanceId == null || instanceId.isBlank()) {
            throw new IllegalStateException("internal-auth 需要 paytech.instance-id 作为实例 ID");
        }
        return instanceId;
    }

    /** {@code internal-auth.mode} 不为 OFF。 */
    static final class ModeEnabledCondition extends SpringBootCondition {

        @Override
        public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String mode = context.getEnvironment().getProperty("internal-auth.mode", InternalAuthMode.OFF.name());
            boolean enabled = !InternalAuthMode.OFF.name().equals(mode.trim().toUpperCase(Locale.ROOT));
            return enabled ? ConditionOutcome.match("internal-auth.mode=" + mode)
                : ConditionOutcome.noMatch("internal-auth.mode=OFF");
        }
    }

    /** 配置了 {@code internal-auth.static-key.private-jwk} 或 {@code public-jwk}。 */
    static final class StaticKeyConfiguredCondition extends SpringBootCondition {

        @Override
        public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
            Environment environment = context.getEnvironment();
            boolean configured = environment.containsProperty("internal-auth.static-key.private-jwk")
                || environment.containsProperty("internal-auth.static-key.public-jwk");
            return configured ? ConditionOutcome.match("static key configured")
                : ConditionOutcome.noMatch("static key not configured");
        }
    }

    /** 有本地密钥对配置，或已有 {@link InternalTokenIssueApi} Bean 定义。 */
    static final class SupplierSourceCondition extends SpringBootCondition {

        @Override
        public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
            Environment environment = context.getEnvironment();
            boolean staticKey = environment.containsProperty("internal-auth.static-key.private-jwk");
            boolean platform = context.getBeanFactory() != null
                && context.getBeanFactory().getBeanNamesForType(InternalTokenIssueApi.class, true, false).length > 0;
            return staticKey || platform ? ConditionOutcome.match("token source available")
                : ConditionOutcome.noMatch("no static key and no InternalTokenIssueApi bean");
        }
    }
}
