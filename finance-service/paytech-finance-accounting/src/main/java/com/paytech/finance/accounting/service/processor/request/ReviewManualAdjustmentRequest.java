package com.paytech.finance.accounting.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/** 复核人工调整（批准或驳回共用）。 */
@Getter
@Setter
public class ReviewManualAdjustmentRequest {
    /** 调整主键 */
    @NotNull
    private Long manualAdjustmentId;

    /** 复核人 */
    @NotNull
    private Long checkerId;

    /** 复核人确认的内容摘要 */
    @NotBlank
    private String approvedContentHash;
}
