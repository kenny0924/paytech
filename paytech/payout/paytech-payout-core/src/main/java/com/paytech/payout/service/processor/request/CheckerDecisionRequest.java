package com.paytech.payout.service.processor.request;

import com.paytech.payout.api.enums.CheckerDecision;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Checker 复核决定命令。 */
@Getter
@AllArgsConstructor
public class CheckerDecisionRequest {
    /** 收款账户主键 */
    @NotNull
    private final Long beneficiaryAccountId;

    /** 决定 */
    @NotNull
    private final CheckerDecision decision;

    /** 拒绝原因；拒绝时必填 */
    private final String reasonCode;

    /** 拒绝时给 Owner 的说明（≤500），可为空；为空时沿用 Maker 建议拒绝时填写的说明；批准时不接受 */
    @Size(max = 500)
    private final String clientNote;

    /** 审阅的内容摘要 */
    @NotBlank
    private final String contentHash;

    /** 期望的账户版本 */
    @NotNull
    private final Long expectedVersion;

    /** Checker 账号 */
    @NotNull
    private final Long actorId;

    /** 来源键（Request ID） */
    @NotBlank
    private final String sourceKey;
}
