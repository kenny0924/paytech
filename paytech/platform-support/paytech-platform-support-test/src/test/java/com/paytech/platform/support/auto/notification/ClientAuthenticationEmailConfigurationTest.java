package com.paytech.platform.support.auto.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.paytech.platform.support.configuration.ClientAuthenticationConfiguration;

/**
 * Client 认证邮件经 Delivery 发送：开启 Client 认证而通知关闭时拒绝启动，不静默丢失邮件。
 *
 * @see ClientAuthenticationConfiguration#ClientAuthenticationConfiguration(org.springframework.core.env.Environment)
 */
class ClientAuthenticationEmailConfigurationTest {

    /** 只装配被测配置类的构造检查。 */
    @Test
    void test_notificationDisabledFailsStartup() {
        new ApplicationContextRunner().withUserConfiguration(ClientAuthenticationConfiguration.class)
            .withPropertyValues("client.authentication.enabled=true", "spring.profiles.active=mock",
                "redis.coordination.enabled=true", "notification.enabled=false")
            .run(context -> assertThat(context).hasFailed().getFailure().rootCause()
                .hasMessageContaining("notification.enabled"));
    }
}
