package com.paytech.reconciliation.service.processor.request;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 执行一轮账单行等待期满检查（XXL 任务 channelLineWaitExpiry）。 */
@Getter
@AllArgsConstructor
public class ExpireWaitingChannelLinesRequest {
    /** 本轮的当前 UTC 时间 */
    @NotNull
    private final Instant now;
}
