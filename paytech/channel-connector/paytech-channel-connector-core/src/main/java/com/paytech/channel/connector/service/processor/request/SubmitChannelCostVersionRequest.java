package com.paytech.channel.connector.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/** 提交渠道成本版本待复核。 */
@Getter
@Setter
public class SubmitChannelCostVersionRequest {
    /** 方案代码 */
    @NotBlank
    private String planCode;

    /** 版本号 */
    @NotNull
    private Integer costVer;

    /** 提交人，必须等于编制人 */
    @NotNull
    private Long makerId;
}
