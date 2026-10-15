package com.paytech.platform.support.auto.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

import com.paytech.platform.support.delivery.core.configuration.NotificationProperties;

/**
 * 通知执行参数的启动校验：默认值合法，零/负重试间隔与过短的未知结果重发窗口拒绝启动。
 * 只装配配置类与 Bean Validation，不启动业务应用。
 *
 * @see NotificationProperties#isRetryDelayPositive()
 * @see NotificationProperties#isUnknownResendDelayBounded()
 */
class NotificationPropertiesTest {

    final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
        .withUserConfiguration(PropertiesOnly.class);

    /** 未配置时使用默认发送次数、重试间隔与 10 分钟重发窗口。 */
    @Test
    void test_defaultsAreValid() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var properties = context.getBean(NotificationProperties.class);
            assertThat(properties.getMaximumSendAttempts()).isEqualTo(3);
            assertThat(properties.getRetryDelay()).isEqualTo(Duration.ofSeconds(30));
            assertThat(properties.getUnknownResendDelay()).isEqualTo(Duration.ofMinutes(10));
        });
    }

    /** 每组只设置一个非法值。 */
    @ParameterizedTest
    @ValueSource(strings = {"notification.retry-delay=0s", "notification.retry-delay=-1s",
        "notification.unknown-resend-delay=30s", "notification.maximum-send-attempts=0"})
    void test_invalidValueFailsStartup(String setting) {
        runner.withPropertyValues(setting).run(context -> assertThat(context).hasFailed());
    }

    /** 仅绑定通知配置。 */
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(NotificationProperties.class)
    static class PropertiesOnly {
    }
}
