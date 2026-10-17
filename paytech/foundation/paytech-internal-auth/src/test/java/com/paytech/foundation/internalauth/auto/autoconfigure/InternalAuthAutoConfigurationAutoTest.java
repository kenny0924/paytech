package com.paytech.foundation.internalauth.auto.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.nimbusds.jose.jwk.ECKey;
import com.paytech.foundation.internalauth.api.InternalTokenKeySource;
import com.paytech.foundation.internalauth.api.InternalTokenSupplier;
import com.paytech.foundation.internalauth.api.InternalTokenVerifier;
import com.paytech.foundation.internalauth.core.autoconfigure.InternalAuthAutoConfiguration;
import com.paytech.foundation.internalauth.core.inbound.InternalCallerAuthenticator;
import com.paytech.foundation.internalauth.core.key.JwksInternalTokenKeySource;
import com.paytech.foundation.internalauth.core.supplier.StaticKeyInternalTokenSupport;
import com.paytech.foundation.internalauth.test.TestKeys;

/**
 * 自动配置：默认 OFF 不创建 Bean；静态密钥仅限非 prod；JWKS 与必需配置的失败启动。
 *
 * @see InternalAuthAutoConfiguration
 */
class InternalAuthAutoConfigurationAutoTest {

    private static final ECKey KEY = TestKeys.generate("local-1");

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(InternalAuthAutoConfiguration.class))
        .withPropertyValues("spring.application.name=" + TestKeys.TARGET, "paytech.instance-id=" + TestKeys.INSTANCE);

    @Test
    void test_normalFlow_offByDefault() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(InternalTokenVerifier.class);
            assertThat(context).doesNotHaveBean(InternalCallerAuthenticator.class);
            assertThat(context).doesNotHaveBean(InternalTokenSupplier.class);
        });
        runner.withPropertyValues("internal-auth.mode=OFF", staticPrivate(), staticPublic())
            .run(context -> assertThat(context).doesNotHaveBean(StaticKeyInternalTokenSupport.class));
    }

    @Test
    void test_normalFlow_staticKeyIssuesAndVerifies() {
        runner.withPropertyValues("internal-auth.mode=REQUIRED", staticPrivate(), staticPublic()).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(InternalCallerAuthenticator.class);
            assertThat(context.getBean(InternalTokenKeySource.class)).isInstanceOf(StaticKeyInternalTokenSupport.class);
            String token = context.getBean(InternalTokenSupplier.class).token(TestKeys.TARGET, "GENERAL");
            assertThat(context.getBean(InternalTokenVerifier.class).verify(token).caller()).isEqualTo(TestKeys.TARGET);
        });
    }

    @Test
    void test_normalFlow_jwksKeySourceWithoutSupplier() {
        runner.withPropertyValues("internal-auth.mode=DUAL", "internal-auth.jwks-uri=http://127.0.0.1:1/jwks")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(InternalTokenKeySource.class)).isInstanceOf(JwksInternalTokenKeySource.class);
                assertThat(context).doesNotHaveBean(InternalTokenSupplier.class);
            });
    }

    @Test
    void test_businessRuleViolation_staticKeyRejectedInProdProfile() {
        runner.withPropertyValues("internal-auth.mode=REQUIRED", "spring.profiles.active=prod", staticPrivate(),
            staticPublic()).run(context -> assertThat(context).getFailure().rootCause()
                .hasMessageContaining("prod Profile"));
    }

    @Test
    void test_invalidParameter_mismatchedStaticKeyPair() {
        String otherPublic = "internal-auth.static-key.public-jwk=" + TestKeys.generate("local-1").toPublicJWK()
            .toJSONString();
        runner.withPropertyValues("internal-auth.mode=REQUIRED", staticPrivate(), otherPublic)
            .run(context -> assertThat(context).getFailure().rootCause().hasMessageContaining("does not match"));
    }

    @Test
    void test_invalidParameter_missingRequiredConfiguration() {
        runner.withPropertyValues("internal-auth.mode=REQUIRED")
            .run(context -> assertThat(context).getFailure().rootCause().hasMessageContaining("jwks-uri"));
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(InternalAuthAutoConfiguration.class))
            .withPropertyValues("internal-auth.mode=REQUIRED", "spring.application.name=card-service", staticPrivate(),
                staticPublic())
            .run(context -> assertThat(context).getFailure().rootCause().hasMessageContaining("paytech.instance-id"));
    }

    private static String staticPrivate() {
        return "internal-auth.static-key.private-jwk=" + KEY.toJSONString();
    }

    private static String staticPublic() {
        return "internal-auth.static-key.public-jwk=" + KEY.toPublicJWK().toJSONString();
    }
}
