package com.paytech.platform.support.configuration;

import java.util.Arrays;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.id.SnowflakeIdGenerator;
import com.paytech.platform.support.accesscontrol.domain.authentication.OperationsPasswordPolicy;
import com.paytech.platform.support.accesscontrol.integration.signing.LocalOperationsTokenCryptography;
import com.paytech.platform.support.accesscontrol.integration.signing.OperationsTokenCryptography;
import com.paytech.platform.support.accesscontrol.integration.vault.LoginEmailVault;
import com.paytech.platform.support.accesscontrol.integration.vault.VaultLoginEmailVault;
import com.paytech.vault.client.VaultClient;

import tools.jackson.databind.ObjectMapper;

/** 开发认证必须显式启用，且只能在 local/auto/test/mock 环境运行；无生产 Mock fallback。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class OperationsAuthenticationConfiguration {
    public OperationsAuthenticationConfiguration(Environment environment) {
        String[] profiles = environment.getActiveProfiles();
        if (profiles.length == 0
            || Arrays.stream(profiles).anyMatch(p -> !p.equals("local") && !p.equals("auto") && !p.equals("test") && !p.equals("mock"))) {
            throw new IllegalStateException("当前认证适配器仅允许显式 local/auto/test/mock，生产必须先接入真实 KMS");
        }
    }

    @Bean
    OperationsPasswordPolicy operationsPasswordPolicy() {
        return new OperationsPasswordPolicy();
    }

    /** 所有环境都经正式 VaultClient 访问 Vault；自动测试使用受控 HTTP Simulator，不在生产代码中保留 Vault Mock。 */
    @Bean
    LoginEmailVault loginEmailVault(VaultClient client) {
        return new VaultLoginEmailVault(client);
    }

    @Bean
    IdGenerator operationsIdGenerator(Environment environment) {
        return new SnowflakeIdGenerator(0,
            environment.getRequiredProperty("operations.authentication.worker-id", Integer.class));
    }

    @Bean
    OperationsTokenCryptography operationsTokenCryptography(Environment environment, ObjectMapper json) {
        return new LocalOperationsTokenCryptography(
            environment.getRequiredProperty("OPERATIONS_LOCAL_SIGNING_PRIVATE_KEY_BASE64"),
            environment.getRequiredProperty("OPERATIONS_LOCAL_HMAC_KEY_BASE64"),
            environment.getRequiredProperty("OPERATIONS_LOCAL_HMAC_KEY_VERSION", Integer.class), json);
    }
}
