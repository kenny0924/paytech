package com.paytech.platform.support.delivery.core.configuration;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import lombok.Getter;
import lombok.Setter;

/** Client Webhook 投递参数；变更需重启，补偿 Policy 在启动时冻结。 */
@Component
@Validated
@Getter
@Setter
@ConfigurationProperties("webhook")
public class WebhookProperties {

    /** 默认关闭；启用还需 compensation.enabled 及 Vault 数据类型与策略。 */
    private boolean enabled;

    /** 通知与人工认证均关闭时必须显式分配的 Snowflake Worker；范围 0..31。 */
    @Min(0)
    @Max(31)
    private Integer workerId;

    /** 每个投递的最大尝试次数，包括首次；用完记 DEAD。 */
    @Min(1)
    @Max(15)
    private int maximumAttempts = 10;

    /** 建立连接的超时。 */
    @NotNull
    private Duration connectTimeout = Duration.ofSeconds(5);

    /** 等待响应的超时。 */
    @NotNull
    private Duration responseTimeout = Duration.ofSeconds(10);

    /** 宽限轮换时旧密钥继续参与签名的时长。 */
    @NotNull
    private Duration secretGracePeriod = Duration.ofHours(1);

    /** 每个 Client 的端点上限，含已停用的端点。 */
    @Min(1)
    private int maximumEndpointsPerClient = 5;

    /**
     * 允许的端点协议
     * <li>生产只允许 https；仅本机测试配置放开 http</li>
     */
    @NotNull
    private List<@NotNull @Pattern(regexp = "https|http") String> allowedSchemes = List.of("https");

    /**
     * 放行的网段（CIDR）
     * <li>默认空：所有内网、回环、链路本地与保留地址都被拒绝；仅本机测试配置放行 127.0.0.1/32</li>
     */
    @NotNull
    private List<@NotNull String> trustedNetworks = List.of();

    /** 运营管理写入的资源锁等待。 */
    @NotNull
    private Duration managementLockWaitTime = Duration.ofMillis(500);

    /** 运营管理写入的资源锁租约。 */
    @NotNull
    private Duration managementLockLeaseTime = Duration.ofSeconds(30);

    /** 启动时拒绝零值与负值的时长。 */
    @AssertTrue(message = "Webhook 超时与宽限时长必须为正数")
    public boolean isDurationsPositive() {
        return positive(connectTimeout) && positive(responseTimeout) && positive(secretGracePeriod)
            && positive(managementLockWaitTime) && positive(managementLockLeaseTime);
    }

    /** 启动时拒绝重复协议，避免配置意图不清。 */
    @AssertTrue(message = "Webhook 允许协议不能为空或重复")
    public boolean isAllowedSchemesDistinct() {
        return allowedSchemes != null && !allowedSchemes.isEmpty()
            && new HashSet<>(allowedSchemes).size() == allowedSchemes.size();
    }

    /** 时长为正数。 */
    static boolean positive(Duration duration) {
        return duration != null && !duration.isZero() && !duration.isNegative();
    }
}
