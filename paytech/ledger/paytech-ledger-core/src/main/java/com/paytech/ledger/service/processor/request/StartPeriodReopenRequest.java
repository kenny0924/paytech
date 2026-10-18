package com.paytech.ledger.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 发起期间重开。 */
@Getter
@Setter
public class StartPeriodReopenRequest {
    /** 会计期间主键 */
    @NotNull
    private Long periodId;

    /** 编制人 */
    @NotNull
    private Long makerId;

    /**
     * 重开原因代码
     * <li>必须属于 PeriodReopenReason 目录。</li>
     */
    @NotBlank
    private String reasonCode;

    /** 批准本次重开的 Platform 工单或操作申请稳定引用，最长 128 字符 */
    @NotBlank
    @Size(max = 128)
    private String requestRef;
}
