package com.paytech.channel.connector.api.channel.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import lombok.Getter;
import lombok.Setter;

/** 能力条件组整组启停请求。 */
@Getter
@Setter
public class ChangeMidCapabilityStatusRequestBody {
    /** 目标状态，ENABLED 或 DISABLED；DISABLED 的组不参与候选匹配 */
    @NotBlank(message = "目标状态必填")
    @Pattern(regexp = "ENABLED|DISABLED", message = "目标状态只能为 ENABLED 或 DISABLED")
    private String targetStatus;
}
