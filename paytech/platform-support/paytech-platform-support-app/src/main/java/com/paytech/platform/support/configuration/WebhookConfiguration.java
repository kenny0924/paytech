package com.paytech.platform.support.configuration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.id.SnowflakeIdGenerator;
import com.paytech.platform.support.delivery.core.configuration.WebhookProperties;

/** Webhook 单独启用时补齐应用 ID 依赖与内部 HTTP 调用方白名单；其他能力开启时沿用其唯一生成器。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "webhook.enabled", havingValue = "true")
@EnableConfigurationProperties(WebhookCallerProperties.class)
public class WebhookConfiguration {

    /** 同一应用只能有一个生成器；Worker 必须由部署显式分配，避免默认碰撞。 */
    @Bean
    @ConditionalOnProperty(name = {"operations.authentication.enabled", "client.authentication.enabled",
        "notification.enabled"}, havingValue = "false", matchIfMissing = true)
    IdGenerator webhookIdGenerator(WebhookProperties properties) {
        if (properties.getWorkerId() == null) {
            throw new IllegalStateException("独立启用 Webhook 必须配置 webhook.worker-id");
        }
        return new SnowflakeIdGenerator(0, properties.getWorkerId());
    }
}
