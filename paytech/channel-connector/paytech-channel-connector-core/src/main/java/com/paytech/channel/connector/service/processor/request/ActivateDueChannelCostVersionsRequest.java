package com.paytech.channel.connector.service.processor.request;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 渠道成本版本到期切换一轮执行。 */
@Getter
@AllArgsConstructor
public class ActivateDueChannelCostVersionsRequest {
    /** 到期判断时间：effective_from 不晚于它的 APPROVED 版本被切换为当前版本。 */
    @NotNull
    private final Instant asOf;
}
