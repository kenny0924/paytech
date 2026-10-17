package com.paytech.platform.support.delivery.integration.provider.ses;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.paytech.vault.client.VaultClient;

/** 通知、SES 与事件拉取同时启用时注册事件队列适配器并开启定时调度；任一关闭都不创建 SQS 客户端。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression(SesEventConfiguration.ENABLED)
@EnableScheduling
@EnableConfigurationProperties(SesEventProperties.class)
public class SesEventConfiguration {

    /** 事件拉取的启用条件，拉取任务与入口共用。 */
    public static final String ENABLED = "'${notification.enabled:false}' == 'true' && "
        + "'${notification.ses.enabled:false}' == 'true' && '${notification.ses.events.enabled:false}' == 'true'";

    /** 按区域复用 SQS 客户端；密钥一律来自 Vault，缺少 VaultClient 时拒绝启动。 */
    @Bean(destroyMethod = "close")
    SesEventQueue sesEventQueue(SesEventProperties properties, VaultClient vaultClient) {
        return new SesEventQueue(properties, new SesCredentialVault(vaultClient));
    }
}
