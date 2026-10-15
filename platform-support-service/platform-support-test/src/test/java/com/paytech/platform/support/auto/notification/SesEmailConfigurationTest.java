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

import com.paytech.platform.support.delivery.core.constant.NotificationProviderTypes;
import com.paytech.platform.support.delivery.integration.provider.ses.SesClients;
import com.paytech.platform.support.delivery.integration.provider.ses.SesEmailConfiguration;
import com.paytech.platform.support.delivery.integration.provider.ses.SesEmailProperties;
import com.paytech.platform.support.delivery.integration.provider.ses.SesEmailProvider;
import com.paytech.vault.client.ProtectCommand;
import com.paytech.vault.client.ProtectResult;
import com.paytech.vault.client.RevealQuery;
import com.paytech.vault.client.RevealResult;
import com.paytech.vault.client.VaultClient;

/**
 * SES 适配器的启动装配：两个开关同时开启才注册 AMAZON_SES 适配器，环境级配置非法时拒绝启动而不是回退。
 * 账号的区域、发件地址与密钥来自账号表，不在部署配置中；只装配被测配置类，不启动业务应用或连接任何网络。
 *
 * @see SesEmailConfiguration#client(SesEmailProperties, String)
 * @see SesEmailProperties#SesEmailProperties(java.net.URI, java.time.Duration, java.time.Duration)
 */
class SesEmailConfigurationTest {

    /** 合法基线；用例只覆盖与场景有关的键。 */
    static final List<String> VALID = List.of("notification.enabled=true", "notification.ses.enabled=true");

    final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(SesEmailConfiguration.class).withBean(VaultClient.class, () -> NO_CALL_VAULT);

    /** 合法配置注册唯一适配器：类型为 AMAZON_SES，不支持查询，客户端按区域延迟创建。 */
    @Test
    void test_enabledAssemblesSingleProvider() {
        runner.withPropertyValues(VALID.toArray(String[]::new)).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(SesEmailProvider.class).hasSingleBean(SesClients.class);
            var provider = context.getBean(SesEmailProvider.class);
            assertThat(provider.providerType()).isEqualTo(NotificationProviderTypes.AMAZON_SES);
            assertThat(provider.supportsQuery()).isFalse();
            assertThat(provider.requestNo(42)).isEqualTo("ses-42");
            var properties = context.getBean(SesEmailProperties.class);
            assertThat(properties.getEndpoint()).isNull();
            assertThat(properties.getConnectTimeout()).hasSeconds(2);
            assertThat(properties.getReadTimeout()).hasSeconds(5);
        });
    }

    /** 同一区域复用一个客户端，不同区域各建一个；关闭后清空。 */
    @Test
    void test_clientsReusedPerRegion() {
        runner.withPropertyValues(VALID.toArray(String[]::new)).run(context -> {
            var clients = context.getBean(SesClients.class);
            var first = clients.client("me-central-1");
            assertThat(clients.client("me-central-1")).isSameAs(first);
            assertThat(clients.client("eu-west-1")).isNotSameAs(first);
            clients.close();
            assertThat(clients.client("me-central-1")).isNotSameAs(first);
        });
    }

    /** 任一开关关闭都不创建 AWS 客户端，也不注册 SES 适配器。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("disabledSwitches")
    void test_disabledCreatesNoClient(String override) {
        runner.withPropertyValues(with(override)).run(context -> assertThat(context).hasNotFailed()
            .doesNotHaveBean(SesClients.class).doesNotHaveBean(SesEmailProvider.class));
    }

    /** 通知或 SES 开关关闭。 */
    static Stream<String> disabledSwitches() {
        return Stream.of("notification.enabled=false", "notification.ses.enabled=false");
    }

    /** 非法环境配置在启动时失败，错误指出具体配置键。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidSettings")
    void test_invalidConfigurationFailsStartup(String override, String key) {
        runner.withPropertyValues(with(override)).run(context -> assertThat(context).hasFailed()
            .getFailure().rootCause().hasMessageContaining(key));
    }

    /** 每组覆盖一个非法值及期望出现在错误中的键。 */
    static Stream<Arguments> invalidSettings() {
        return Stream.of(Arguments.of("notification.ses.endpoint=http://email.example.com", "endpoint"),
            Arguments.of("notification.ses.endpoint=https://user@email.example.com", "endpoint"),
            Arguments.of("notification.ses.endpoint=https://email.example.com?x=1", "endpoint"),
            Arguments.of("notification.ses.endpoint=https:///ses", "endpoint"),
            Arguments.of("notification.ses.connect-timeout=0s", "connect-timeout"),
            Arguments.of("notification.ses.read-timeout=30s", "read-timeout"));
    }

    /** 本机 HTTP 终端被接受。 */
    @Test
    void test_loopbackEndpointAccepted() {
        runner.withPropertyValues(with("notification.ses.endpoint=http://127.0.0.1:8080/ses")).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(SesEmailProvider.class);
            assertThat(context.getBean(SesEmailProperties.class).getEndpoint())
                .hasToString("http://127.0.0.1:8080/ses");
        });
    }

    /** 密钥一律来自 Vault：没有 VaultClient 时拒绝启动，不回退默认凭据链。 */
    @Test
    void test_withoutVaultClientFailsStartup() {
        new ApplicationContextRunner().withUserConfiguration(SesEmailConfiguration.class)
            .withPropertyValues(VALID.toArray(String[]::new))
            .run(context -> assertThat(context).hasFailed().getFailure().rootCause()
                .hasMessageContaining("VaultClient"));
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
