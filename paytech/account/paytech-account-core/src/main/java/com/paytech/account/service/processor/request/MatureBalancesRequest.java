package com.paytech.account.service.processor.request;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 到期转可用一轮执行。 */
@Getter
@AllArgsConstructor
public class MatureBalancesRequest {
    /** 到期判断时间：balance_available_time 不晚于它的 PENDING 事实被处理。 */
    @NotNull
    private final Instant asOf;
}
