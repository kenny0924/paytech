package com.paytech.acquiring.service.processor.order.request;

import java.time.Duration;
import java.time.Instant;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一次过期扫描的分片与分页参数。 */
@Getter
@AllArgsConstructor
public class ScanExpiredOrdersRequest {
    /**
     * 分片序号
     * <li>从 0 开始，必须小于分片总数；按 order_id 对分片总数取模选择订单。</li>
     */
    @PositiveOrZero
    private final int shardIndex;

    /** 分片总数；单实例为 1，退化为全量扫描。 */
    @Positive
    private final int shardTotal;

    /** 每页订单数，1—1000。 */
    @Positive
    @Max(1000)
    private final int batchSize;

    /** 单次执行最多页数，限制单次任务时长。 */
    @Positive
    private final int maxBatches;

    /** 本轮扫描时刻，选择 expires_at 不晚于该时刻的订单。 */
    @NotNull
    private final Instant now;

    /** 终止意图写入后仍 PENDING 的告警阈值，逐单传给过期收尾。 */
    @NotNull
    private final Duration pendingAlertAfter;
}
