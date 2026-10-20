package com.paytech.billing.service.processor.request;

import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/** 作废计费事实草稿；作废人由入口记日志，不落库。 */
@Getter
@Setter
public class VoidBillingFactRequest {
    /** 计费事实 ID */
    @NotNull
    private Long factId;
}
