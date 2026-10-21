package com.paytech.reconciliation.service.processor.request;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.paytech.reconciliation.api.enums.ReconClassification;
import com.paytech.reconciliation.api.enums.RematchTargetType;
import com.paytech.reconciliation.api.enums.ResolutionType;
import lombok.Builder;
import lombok.Getter;

/** 提交异常处置命令（Maker）；字段组合按处置方式在 Processor 中校验。 */
@Getter
@Builder
public class SubmitReconResolutionRequest {
    /** 提交人 Operations Account ID */
    @NotNull
    private final Long actorId;

    /** 异常主键 */
    @NotNull
    private final Long exceptionId;

    /** 处置方式 */
    @NotNull
    private final ResolutionType resolutionType;

    /** 归类码；仅 CLASSIFY */
    private final ReconClassification classificationCode;

    /** 归类金额（带符号）；仅 CLASSIFY */
    private final BigDecimal amount;

    /** 重新匹配目标类型；仅 RE_MATCH */
    private final RematchTargetType rematchTargetType;

    /** 重新匹配目标主键；仅 RE_MATCH */
    private final Long rematchTargetId;

    /** 银行层重新匹配的分配金额；仅银行层 RE_MATCH */
    private final BigDecimal rematchAmount;

    /** 处置说明 */
    @NotBlank
    private final String note;
}
