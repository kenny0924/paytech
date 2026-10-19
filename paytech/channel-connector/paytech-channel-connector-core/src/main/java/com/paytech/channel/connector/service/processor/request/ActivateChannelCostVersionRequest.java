package com.paytech.channel.connector.service.processor.request;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 把一个已到生效时间的 APPROVED 渠道成本版本切换为当前版本。 */
@Getter
@AllArgsConstructor
public class ActivateChannelCostVersionRequest {
    /** 方案代码 */
    @NotBlank
    private final String planCode;

    /** 版本号 */
    @NotNull
    private final Integer costVer;

    /** 到期判断时间 */
    @NotNull
    private final Instant asOf;
}
