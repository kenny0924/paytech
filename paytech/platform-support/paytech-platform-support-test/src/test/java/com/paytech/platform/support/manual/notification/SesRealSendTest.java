package com.paytech.platform.support.manual.notification;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;

import com.paytech.platform.support.delivery.core.constant.NotificationProviderTypes;
import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationProvider;
import com.paytech.platform.support.delivery.integration.provider.EmailProviderRequest;
import com.paytech.platform.support.delivery.integration.provider.EmailProviderResult;
import com.paytech.platform.support.delivery.integration.provider.ses.SesClients;
import com.paytech.platform.support.delivery.integration.provider.ses.SesCredentialVault;
import com.paytech.platform.support.delivery.integration.provider.ses.SesEmailProperties;
import com.paytech.platform.support.delivery.integration.provider.ses.SesEmailProvider;
import com.paytech.vault.client.ProtectCommand;
import com.paytech.vault.client.ProtectResult;
import com.paytech.vault.client.RevealQuery;
import com.paytech.vault.client.RevealResult;
import com.paytech.vault.client.VaultClient;

/**
 * 手动联调：经生产 SES 适配器与客户端向真实 Amazon SES 发送一封纯文本邮件，验证密钥、区域、发件身份和线缆协议。
 * 默认 Maven 不运行；需显式执行 {@code -Pmanual-test -Dtest=SesRealSendTest}。
 *
 * <p>
 * 环境变量：SES_MANUAL_REGION、SES_MANUAL_FROM（已验证身份）、SES_MANUAL_TO（沙箱下也须已验证）、
 * SES_MANUAL_ACCESS_KEY_ID、SES_MANUAL_SECRET_ACCESS_KEY，可选 SES_MANUAL_CONFIGURATION_SET。
 * 密钥只在进程内存中，由测试侧 VaultClient 按 Reveal 合同返回，不写入文件。
 *
 * @see SesEmailProvider#send(NotificationProvider, EmailProviderRequest)
 */
class SesRealSendTest {

    /** 真实发送一封邮件并要求 SES 受理返回 MessageId。 */
    @Test
    void test_realSendAccepted() {
        var account = new NotificationProvider();
        account.setProviderCode("NOTIFICATION_SES_MANUAL");
        account.setProviderType(NotificationProviderTypes.AMAZON_SES);
        account.setRegion(required("SES_MANUAL_REGION"));
        account.setFromAddress(required("SES_MANUAL_FROM"));
        account.setConfigurationSet(System.getenv("SES_MANUAL_CONFIGURATION_SET"));
        account.setCredentialVaultToken("vlt_manualCredentialTokenFromEnvironment000");
        try (var clients = new SesClients(new SesEmailProperties(null, null, null))) {
            var provider = new SesEmailProvider(clients, new SesCredentialVault(ENVIRONMENT_VAULT));

            var result = provider.send(account, new EmailProviderRequest(provider.requestNo(System.currentTimeMillis()),
                required("SES_MANUAL_TO"), "Code-MoR SES 联调",
                "SES 手动联调邮件，发送时间 " + Instant.now().truncatedTo(ChronoUnit.SECONDS)));

            assertEquals(EmailProviderResult.Outcome.ACCEPTED, result.getOutcome(), "SES 结果码：" + result.getCode());
            assertNotNull(result.getMessageId());
        }
    }

    /** 测试侧 Vault：按 Reveal 合同返回环境变量中的密钥对，不允许 Protect。 */
    static final VaultClient ENVIRONMENT_VAULT = new VaultClient() {
        /** 手动联调不写入 Vault。 */
        @Override
        public ProtectResult protect(ProtectCommand command) {
            throw new AssertionError("手动联调不得 Protect");
        }

        /** 返回 NOTIFICATION_PROVIDER_CREDENTIAL_V1 格式的密钥对。 */
        @Override
        public RevealResult reveal(RevealQuery query) {
            return new RevealResult("{\"accessKeyId\":\"" + required("SES_MANUAL_ACCESS_KEY_ID")
                + "\",\"secretAccessKey\":\"" + required("SES_MANUAL_SECRET_ACCESS_KEY") + "\"}");
        }
    };

    /** 缺少环境变量时直接失败并给出设置方法，不静默跳过。 */
    static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            fail("请设置环境变量 " + name + "（另需 SES_MANUAL_REGION/SES_MANUAL_FROM/SES_MANUAL_TO 及"
                + " SES_MANUAL_ACCESS_KEY_ID/SES_MANUAL_SECRET_ACCESS_KEY），再执行 -Pmanual-test");
        }
        return value;
    }
}
