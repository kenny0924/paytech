package com.paytech.payment.card.core.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 卡工具配置 */
@Data
@Configuration(proxyBeanMethods = false)
@ConfigurationProperties(prefix = "payment.card")
public class PaymentCardProperties {
    /**
     * 启用合成卡 Mock
     * <li>默认关闭；仅用于本地合成卡流程</li>
     */
    private boolean mockEnabled;
}
