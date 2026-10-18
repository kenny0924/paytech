package com.paytech.card.service.processor.request;

import java.time.Duration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 孤立卡交易工具回收命令：过期超过宽限期的工具才回收 */
@Getter
@AllArgsConstructor
public class PurgeOrphanCardInstrumentsRequest {
    /** 工具过期后保留的宽限期，不得为负 */
    @NotNull(message = "宽限期必填")
    private final Duration grace;

    /** 单批最多回收条数 */
    @Min(value = 1, message = "单批条数至少 1")
    @Max(value = 1000, message = "单批条数最多 1000")
    private final int batchSize;
}
