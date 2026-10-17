package com.paytech.platform.support.delivery.core.configuration;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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

    /**
     * 结果未知后的安全重发窗口
     * <li>仅用于不支持查询的 Provider：从该次尝试开始时间起算，窗口内不重发，之后新建尝试</li>
     * <li>默认 10 分钟，至少 1 分钟；须远大于供应商调用超时，降低“已受理但未收到响应”时的重复概率</li>
     */
    @NotNull
    private Duration unknownResendDelay = Duration.ofMinutes(10);

    /**
     * 只能经即时发送入口使用的模板编码（如认证邮件）
     * <li>即时发送只接受集合内模板；普通通知提交与 HTTP 白名单不得使用集合内模板</li>
     * <li>默认为空，即时发送全部拒绝</li>
     */
    @NotNull
    private List<@NotNull @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String> transientTemplateCodes = List.of();

    /** 模板是否只用于即时发送。 */
    public boolean isTransientTemplate(String templateCode) {
        return transientTemplateCodes.contains(templateCode);
    }

    /** 启动时拒绝重复编码，避免配置意图不清。 */
    @jakarta.validation.constraints.AssertTrue(message = "即时发送模板编码不能重复")
    public boolean isTransientTemplateCodesDistinct() {
        return new HashSet<>(transientTemplateCodes).size() == transientTemplateCodes.size();
    }

    /** 启动时拒绝零值和负值，防止发送重试形成忙循环。 */
    @jakarta.validation.constraints.AssertTrue(message = "通知重试间隔必须为正数")
    public boolean isRetryDelayPositive() {
        return retryDelay != null && !retryDelay.isZero() && !retryDelay.isNegative();
    }

    /** 启动时拒绝过短窗口，避免调用仍在进行时即判定可重发。 */
    @jakarta.validation.constraints.AssertTrue(message = "通知未知结果重发窗口不能短于 1 分钟")
    public boolean isUnknownResendDelayBounded() {
        return unknownResendDelay != null && unknownResendDelay.compareTo(Duration.ofMinutes(1)) >= 0;
    }
}
