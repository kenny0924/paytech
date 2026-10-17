package com.paytech.billing.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/** 创建渠道成本方案。 */
@Getter
@Setter
public class CreateChannelCostPlanRequest {
    /** Payment Channel 稳定业务 ID */
    @NotNull
    private Long channelId;

    /** 方案代码 */
    @NotBlank
    private String planCode;
}
