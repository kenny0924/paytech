package com.paytech.finance.accounting.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/** 复核渠道成本版本（批准或驳回共用）。 */
@Getter
@Setter
public class ReviewChannelCostVersionRequest {
    /** 方案代码 */
    @NotBlank
    private String planCode;

    /** 版本号 */
    @NotNull
    private Integer costVer;

    /** 复核人，必须不同于编制人 */
    @NotNull
    private Long checkerId;

    /** 复核人确认的内容摘要，必须等于版本 contentHash */
    @NotBlank
    private String approvedContentHash;
}
