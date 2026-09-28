package com.paytech.platform.support.configuration;

import java.util.Arrays;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.id.SnowflakeIdGenerator;
import com.paytech.platform.support.accesscontrol.domain.authentication.ClientEmailChallengePolicy;
import com.paytech.platform.support.accesscontrol.domain.authentication.OperationsPasswordPolicy;
import com.paytech.platform.support.accesscontrol.integration.mail.AuthenticationEmailSender;
import com.paytech.platform.support.accesscontrol.integration.signing.HumanAuthenticationCryptography;
import com.paytech.platform.support.accesscontrol.integration.signing.LocalHumanAuthenticationCryptography;
import com.paytech.platform.support.accesscontrol.integration.vault.LoginEmailVault;
import com.paytech.platform.support.accesscontrol.integration.vault.VaultLoginEmailVault;
import com.paytech.platform.support.integration.mail.SmtpAuthenticationEmailSender;
import com.paytech.vault.client.VaultClient;
import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.databind.ObjectMapper;

/** Client 本地研发认证配置；本地密钥适配器不能作为生产 KMS 自动降级。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "client.authentication.enabled", havingValue = "true")
public class ClientAuthenticationConfiguration {
    /** 当前本地加密适配器只允许明确的 local/auto/test 环境。 */
    public ClientAuthenticationConfiguration(Environment environment) {
        String[] profiles = environment.getActiveProfiles();
        if (profiles.length == 0 || Arrays.stream(profiles).anyMatch(profile ->
            !profile.equals("local") && !profile.equals("auto") && !profile.equals("test"))) {
            throw new IllegalStateException("当前 Client 密钥适配器仅用于 local/auto/test，生产须接入真实 KMS");
        }
        if (!environment.getProperty("redis.coordination.enabled", Boolean.class, false)) {
            throw new IllegalStateException("Client 认证必须开启 redis.coordination.enabled 并配置本地 Redis");
        }
    }

    /** 注册链接使用无外部状态的固定协议规则。 */
    @Bean
    ClientEmailChallengePolicy clientEmailChallengePolicy() { return new ClientEmailChallengePolicy(); }

    /** 显式限定 CLIENT Vault；不参与 Operations 未限定注入，测试使用受控 HTTP Simulator。 */
    @Bean(defaultCandidate = false)
    LoginEmailVault clientLoginEmailVault(VaultClient client) { return new VaultLoginEmailVault(client); }

    /** CUSTOMER 密钥只能由安全配置注入，命名空间与 Operations 分离。 */
    @Bean
    HumanAuthenticationCryptography clientAuthenticationCryptography(Environment environment, ObjectMapper json) {
        return new LocalHumanAuthenticationCryptography(
            environment.getRequiredProperty("CLIENT_LOCAL_SIGNING_PRIVATE_KEY_BASE64"),
            environment.getRequiredProperty("CLIENT_LOCAL_HMAC_KEY_BASE64"),
            environment.getRequiredProperty("CLIENT_LOCAL_HMAC_KEY_VERSION", Integer.class), json, "CUSTOMER");
    }

    /** 邮件走可替换 SMTP 端口，发件人与站点由部署配置绑定。 */
    @Bean
    AuthenticationEmailSender authenticationEmailSender(JavaMailSender mail, Environment environment,
        OperationsPasswordPolicy passwords, MeterRegistry metrics) {
        String from = passwords.canonicalEmail(environment.getRequiredProperty("client.authentication.mail-from"));
        return new SmtpAuthenticationEmailSender(mail, from,
            environment.getRequiredProperty("client.authentication.public-origin"), metrics);
    }

    /** 同一应用仅持有一个 Snowflake 生成器，避免两个相同 Worker 产生重复 ID。 */
    @Bean
    @ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "false", matchIfMissing = true)
    IdGenerator customerIdGenerator(Environment environment) {
        return new SnowflakeIdGenerator(0, environment.getRequiredProperty("client.authentication.worker-id", Integer.class));
    }

    /** Client 单独启用时沿用现有人工密码规则，开发弱密码集不冒充生产语料。 */
    @Bean
    @ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "false", matchIfMissing = true)
    OperationsPasswordPolicy customerPasswordPolicy() { return new OperationsPasswordPolicy(); }
}
