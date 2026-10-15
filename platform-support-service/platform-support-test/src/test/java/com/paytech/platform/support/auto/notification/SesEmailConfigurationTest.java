package com.paytech.platform.support.auto.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.paytech.platform.support.delivery.integration.provider.ses.SesEmailConfiguration;
import com.paytech.platform.support.delivery.integration.provider.ses.SesEmailProperties;
import com.paytech.platform.support.delivery.integration.provider.ses.SesEmailProvider;

import com.paytech.vault.client.ProtectCommand;
import com.paytech.vault.client.ProtectResult;
import com.paytech.vault.client.RevealQuery;
import com.paytech.vault.client.RevealResult;
import com.paytech.vault.client.VaultClient;

import software.amazon.awssdk.services.sesv2.SesV2Client;

/**
 * SES 账号的启动装配：两个开关同时开启才创建 AWS 客户端，配置非法时拒绝启动而不是回退。
 * 只装配被测配置类，不启动业务应用或连接任何网络。
 *
 * @see SesEmailConfiguration#client(SesEmailProperties)
 * @see SesEmailProperties#SesEmailProperties(String, String, String, String, java.net.URI, String,
 *      java.time.Duration, java.time.Duration)
 */
class SesEmailConfigurationTest {

    /** 合法基线；用例只覆盖与场景有关的键。 */
    static final List<String> VALID = List.of("notification.enabled=true", "notification.ses.enabled=true",
        "notification.ses.provider-code=NOTIFICATION_SES", "notification.ses.region=me-central-1",
        "notification.ses.from-address= NoReply@Example.com ", "notification.ses.configuration-set=",
        "notification.ses.credential-token=");

    final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(SesEmailConfiguration.class);

    /** 合法配置装配唯一适配器：账号编码来自配置，不支持查询，空白可选项视为未配置，发件人规范化。 */
    @Test
    void test_enabledAssemblesSingleProvider() {
        runner.withPropertyValues(VALID.toArray(String[]::new)).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(SesEmailProvider.class).hasSingleBean(SesV2Client.class);
            var provider = context.getBean(SesEmailProvider.class);
            assertThat(provider.providerCode()).isEqualTo("NOTIFICATION_SES");
            assertThat(provider.supportsQuery()).isFalse();
            assertThat(provider.requestNo(42)).isEqualTo("ses-42");
            var properties = context.getBean(SesEmailProperties.class);
            assertThat(properties.getFromAddress()).isEqualTo("noreply@example.com");
            assertThat(properties.getConfigurationSet()).isNull();
            assertThat(properties.usesVaultCredentials()).isFalse();
        });
    }

    /** 任一开关关闭都不创建 AWS 客户端，也不绑定 SES 配置。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("disabledSwitches")
    void test_disabledCreatesNoClient(String override) {
        runner.withPropertyValues(with(override)).run(context -> assertThat(context).hasNotFailed()
            .doesNotHaveBean(SesV2Client.class).doesNotHaveBean(SesEmailProvider.class));
    }

    /** 通知或 SES 开关关闭。 */
    static Stream<String> disabledSwitches() {
        return Stream.of("notification.enabled=false", "notification.ses.enabled=false");
    }

    /** 非法配置在启动时失败，错误指出具体配置键，不回退为默认账号或 Mock。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidSettings")
    void test_invalidConfigurationFailsStartup(String override, String key) {
        runner.withPropertyValues(with(override)).run(context -> assertThat(context).hasFailed()
            .getFailure().rootCause().hasMessageContaining(key));
    }

    /** 每组覆盖一个非法值及期望出现在错误中的键。 */
    static Stream<Arguments> invalidSettings() {
        return Stream.of(Arguments.of("notification.ses.provider-code=lower_case", "provider-code"),
            Arguments.of("notification.ses.region= ", "region"),
            Arguments.of("notification.ses.from-address=Ops <ops@example.com>", "from-address"),
            Arguments.of("notification.ses.configuration-set=bad set", "configuration-set"),
            Arguments.of("notification.ses.endpoint=http://email.example.com", "endpoint"),
            Arguments.of("notification.ses.endpoint=https://user@email.example.com", "endpoint"),
            Arguments.of("notification.ses.endpoint=https://email.example.com?x=1", "endpoint"),
            Arguments.of("notification.ses.credential-token=bad token!", "credential-token"),
            Arguments.of("notification.ses.credential-token=" + "t".repeat(42), "credential-token"),
            Arguments.of("notification.ses.endpoint=https:///ses", "endpoint"),
            Arguments.of("notification.ses.connect-timeout=0s", "connect-timeout"),
            Arguments.of("notification.ses.read-timeout=30s", "read-timeout"));
    }

    /** 必填项完全缺失（而非空白）时同样拒绝启动。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("requiredKeys")
    void test_missingRequiredKeyFailsStartup(String key) {
        var values = VALID.stream().filter(value -> !value.startsWith("notification.ses." + key + "=")).toList();
        runner.withPropertyValues(values.toArray(String[]::new)).run(context -> assertThat(context).hasFailed()
            .getFailure().rootCause().hasMessageContaining(key));
    }

    /** 三个必填配置键。 */
    static Stream<String> requiredKeys() {
        return Stream.of("provider-code", "region", "from-address");
    }

    /** 本机 HTTP 终端与 Vault 凭据 Token 被接受；Token 不出现在配置的字符串形式中。 */
    @Test
    void test_loopbackEndpointAndVaultTokenAccepted() {
        runner.withBean(VaultClient.class, () -> NO_CALL_VAULT)
            .withPropertyValues(with("notification.ses.endpoint=http://127.0.0.1:8080/ses",
                "notification.ses.credential-token=vlt_syntheticTokenForAssemblyOnly0000000000")).run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(SesEmailProvider.class);
                    var properties = context.getBean(SesEmailProperties.class);
                    assertThat(properties.usesVaultCredentials()).isTrue();
                    assertThat(properties.getEndpoint()).hasToString("http://127.0.0.1:8080/ses");
                    assertThat(properties.toString()).doesNotContain("vlt_syntheticTokenForAssemblyOnly0000000000");
                });
    }

    /** 配置了凭据 Token 但没有 VaultClient 时拒绝启动，不回退默认凭据链。 */
    @Test
    void test_vaultTokenWithoutVaultClientFailsStartup() {
        runner.withPropertyValues(with("notification.ses.credential-token=vlt_syntheticTokenForAssemblyOnly0000000000"))
            .run(context -> assertThat(context).hasFailed().getFailure().rootCause().hasMessageContaining("VaultClient"));
    }

    /** 仅用于装配检查的 VaultClient：本测试不发送邮件，任何调用都说明装配阶段越界访问了 Vault。 */
    static final VaultClient NO_CALL_VAULT = new VaultClient() {
        /** 装配阶段不得 Protect。 */
        @Override
        public ProtectResult protect(ProtectCommand command) {
            throw new AssertionError("装配阶段不得调用 Vault");
        }

        /** 装配阶段不得 Reveal。 */
        @Override
        public RevealResult reveal(RevealQuery query) {
            throw new AssertionError("装配阶段不得调用 Vault");
        }
    };

    /** 在合法基线后追加覆盖项，后者优先。 */
    static String[] with(String... overrides) {
        var values = new ArrayList<>(VALID);
        values.addAll(List.of(overrides));
        return values.toArray(String[]::new);
    }
}
