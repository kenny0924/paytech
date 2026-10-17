package com.paytech.billing.service.processor.request;

import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/** 批准经济事实。 */
@Getter
@Setter
public class ApproveEconomicFactRequest {
    /** 经济事实 ID */
    @NotNull
    private Long factId;

    /** 复核人，必须不同于编制人 */
    @NotNull
    private Long checkerId;
}
