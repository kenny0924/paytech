package com.paytech.acquiring.configuration;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** 订单过期扫描任务的配置，由 XXL-Job 入口每次执行时读取。 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties("acquiring.order-expiry")
public class OrderExpiryProperties {
    /**
     * 每页读取的到期订单数
     * <li>默认 200，合法范围 1—1000。</li>
     */
    @Min(1)
    @Max(1000)
    private int batchSize = 200;

    /**
     * 单次执行最多读取的页数
     * <li>默认 50，合法范围 1—1000；用于限制单次任务时长，未处理的订单留待下一轮。</li>
     */
    @Min(1)
    @Max(1000)
    private int maxBatches = 50;

    /**
     * 终止意图写入后仍为 PENDING 的告警阈值
     * <li>默认 24 小时；超过后每轮记录一条 WARN 转人工，订单仍保持 OPEN。</li>
     */
    @NotNull
    private Duration pendingAlertAfter = Duration.ofHours(24);
}
