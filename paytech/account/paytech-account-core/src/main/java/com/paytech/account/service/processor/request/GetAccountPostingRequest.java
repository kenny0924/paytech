package com.paytech.account.service.processor.request;

import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按主键查询入账批次。 */
@Getter
@AllArgsConstructor
public class GetAccountPostingRequest {
    /** 批次主键。 */
    @Positive
    private final long postingId;
}
