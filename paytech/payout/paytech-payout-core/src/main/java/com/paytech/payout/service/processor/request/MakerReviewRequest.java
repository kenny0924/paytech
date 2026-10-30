package com.paytech.payout.service.processor.request;

import com.paytech.payout.api.enums.MakerReviewDecision;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Maker 核验意见命令。 */
@Getter
@AllArgsConstructor
public class MakerReviewRequest {
    /** 收款账户主键 */
    @NotNull
    private final Long beneficiaryAccountId;

    /** 意见 */
    @NotNull
    private final MakerReviewDecision decision;

    /** 拒绝原因；建议拒绝时必填 */
    private final String reasonCode;

    /** 核验意见备注（内部，≤500），可为空 */
    @Size(max = 500)
    private final String note;

    /** 建议拒绝时给 Owner 的说明（≤500），可为空；建议通过时不接受 */
    @Size(max = 500)
    private final String clientNote;

    /** 审阅的内容摘要 */
    @NotBlank
    private final String contentHash;

    /** 期望的账户版本 */
    @NotNull
    private final Long expectedVersion;

    /** Maker 账号 */
    @NotNull
    private final Long actorId;

    /** 来源键（Request ID） */
    @NotBlank
    private final String sourceKey;
}
