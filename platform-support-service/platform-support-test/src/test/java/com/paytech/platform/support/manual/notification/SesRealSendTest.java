package com.paytech.platform.support.manual.notification;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.paytech.platform.support.delivery.integration.provider.EmailProviderRequest;
import com.paytech.platform.support.delivery.integration.provider.EmailProviderResult;
import com.paytech.platform.support.delivery.integration.provider.ses.SesEmailConfiguration;
import com.paytech.platform.support.delivery.integration.provider.ses.SesEmailProperties;
import com.paytech.platform.support.delivery.integration.provider.ses.SesEmailProvider;

/**
 * 手动联调：经生产 SES 客户端向真实 Amazon SES 发送一封纯文本邮件，验证凭据、区域、发件身份和线缆协议。
 * 默认 Maven 不运行；需显式执行 {@code -Pmanual-test -Dtest=SesRealSendTest}。
 *
 * <p>
 * 环境变量：SES_MANUAL_REGION、SES_MANUAL_FROM（已验证身份）、SES_MANUAL_TO（沙箱下也须已验证），
 * 可选 SES_MANUAL_CONFIGURATION_SET；凭据走 AWS 默认凭据链（如 AWS_PROFILE），不写入文件。
 *
 * @see SesEmailProvider#send(EmailProviderRequest)
 */
class SesRealSendTest {

    /** 真实发送一封邮件并要求 SES 受理返回 MessageId。 */
    @Test
    void test_realSendAccepted() {
        var properties = new SesEmailProperties("NOTIFICATION_SES_MANUAL", required("SES_MANUAL_REGION"),
            required("SES_MANUAL_FROM"), System.getenv("SES_MANUAL_CONFIGURATION_SET"), null, null, null, null);
        try (var client = SesEmailConfiguration.client(properties)) {
            var provider = new SesEmailProvider(client, properties, null);

            var result = provider.send(new EmailProviderRequest(provider.requestNo(System.currentTimeMillis()),
                required("SES_MANUAL_TO"), "Code-MoR SES 联调", "SES 手动联调邮件，发送时间 " + Instant.now()));

            assertEquals(EmailProviderResult.Outcome.ACCEPTED, result.getOutcome(), "SES 结果码：" + result.getCode());
            assertNotNull(result.getMessageId());
        }
    }

    /** 缺少环境变量时直接失败并给出设置方法，不静默跳过。 */
    static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            fail("请设置环境变量 " + name + "（另需 SES_MANUAL_REGION/SES_MANUAL_FROM/SES_MANUAL_TO 及 AWS 默认凭据），再执行 -Pmanual-test");
        }
        return value;
    }
}
