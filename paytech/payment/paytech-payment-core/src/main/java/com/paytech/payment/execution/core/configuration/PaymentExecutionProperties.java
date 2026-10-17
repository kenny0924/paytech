package com.paytech.payment.execution.core.configuration;

import java.time.Duration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 支付执行配置
 *
 * @author lzb
 */
@Data
@Configuration(proxyBeanMethods = false)
@ConfigurationProperties(prefix = "payment.execution")
public class PaymentExecutionProperties {
    /**
     * 支付工具申请有效期
     * <li>使用 Duration，例如 PT5M；零值或负值不接受新申请</li>
     * <li>实际到期时间不超过 Intent 和 Card 返回的期限</li>
     */
    private Duration instrumentPreparationWindow = Duration.ZERO;
}
