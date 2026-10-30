package com.paytech.payout.core.configuration;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

/** 收款账户状态变化通知（R7、technique 8.3）的配置。 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties("payout.notification")
public class PayoutNotificationProperties {
    /**
     * 调用 Platform 通知时的调用方服务名（X-Caller-Service）
     * <li>必须登记在 Platform notification.http.callers；默认 payout-service。</li>
     */
    @NotBlank
    private String callerService = "payout-service";

    /**
     * 通知默认语言（BCP 47）
     * <li>默认 en；收件人语言由 Platform 按成员偏好覆盖。</li>
     */
    @NotBlank
    private String defaultLocale = "en";

    /**
     * Owner 类型 → 收件人判定权限
     * <li>Platform 把该 Owner 租户内具备此权限的有效成员解析为收件人（S-05）。</li>
     * <li>只配置 CLIENT：CLIENT_PAYOUT_ACCOUNT_MANAGE（technique 8.6）；Local Partner 没有门户与权限码，不发通知。</li>
     */
    @NotEmpty
    private Map<String, String> recipientPermissions = new LinkedHashMap<>();
}
