package com.paytech.platform.support.delivery.core.configuration;

import java.time.Duration;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import lombok.Getter;
import lombok.Setter;

/** 通知执行参数；变更需重启，补偿 Policy 在启动时冻结。 */
@Component
@Validated
@Getter
@Setter
@ConfigurationProperties("notification")
public class NotificationProperties {

    /** 默认关闭；启用还需 compensation.enabled 及 Vault 和 Provider 适配器。 */
    private boolean enabled;

    /** 人工认证均关闭时必须显式分配的 Snowflake Worker；范围 0..31。 */
    @Min(0)
    @jakarta.validation.constraints.Max(31)
    private Integer workerId;

    /** 每个收件人最大实际发送次数，包括首次。 */
    @Min(1)
    private int maximumSendAttempts = 3;

    /** 明确失败后等待新发送的最短间隔。 */
    @NotNull
    private Duration retryDelay = Duration.ofSeconds(30);

    /** 启动时拒绝零值和负值，防止发送重试形成忙循环。 */
    @jakarta.validation.constraints.AssertTrue(message = "通知重试间隔必须为正数")
    public boolean isRetryDelayPositive() {
        return retryDelay != null && !retryDelay.isZero() && !retryDelay.isNegative();
    }
}
