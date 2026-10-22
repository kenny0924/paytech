package com.paytech.acquiring.service.processor.order.request;

import java.time.Duration;
import java.time.Instant;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 单个订单的过期收尾请求，由过期扫描逐单构造。 */
@Getter
@AllArgsConstructor
public class ExpireOrderRequest {
    /** 待收尾的订单主键。 */
    @Positive
    private final long orderId;

    /**
     * 本轮扫描时刻
     * <li>用于重新判断到期与终止未决告警；写入时刻另取当前时间。</li>
     */
    @NotNull
    private final Instant now;

    /**
     * 终止意图写入后仍 PENDING 的告警阈值
     * <li>由扫描任务从配置读取后逐单传入；超过后记录一条 WARN 转人工。</li>
     */
    @NotNull
    private final Duration pendingAlertAfter;
}
