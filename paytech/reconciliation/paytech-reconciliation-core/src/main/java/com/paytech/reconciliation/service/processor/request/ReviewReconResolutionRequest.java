package com.paytech.reconciliation.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.paytech.reconciliation.api.enums.CheckerDecision;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 复核异常处置命令（Checker）。 */
@Getter
@AllArgsConstructor
public class ReviewReconResolutionRequest {
    /** 复核人 Operations Account ID */
    @NotNull
    private final Long actorId;

    /** 处置主键 */
    @NotNull
    private final Long resolutionId;

    /** 复核决定 */
    @NotNull
    private final CheckerDecision decision;

    /** 复核人确认的内容摘要 */
    @NotBlank
    private final String approvedContentHash;
}
