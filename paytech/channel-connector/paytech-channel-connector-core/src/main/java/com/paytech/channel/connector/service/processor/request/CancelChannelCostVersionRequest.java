package com.paytech.channel.connector.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 作废未到生效时间的已批准渠道成本版本。 */
@Getter
@Setter
public class CancelChannelCostVersionRequest {
    /** 方案代码 */
    @NotBlank
    private String planCode;

    /** 版本号 */
    @NotNull
    private Integer costVer;

    /** 作废人 Platform Actor 稳定业务 ID */
    @NotNull
    private Long operatorId;

    /** 作废原因，写入变更记录 */
    @NotBlank
    @Size(max = 512)
    private String reason;
}
