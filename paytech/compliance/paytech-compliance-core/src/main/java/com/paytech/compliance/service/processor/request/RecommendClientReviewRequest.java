package com.paytech.compliance.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 推荐人给出推荐结论。 */
@Getter
@AllArgsConstructor
public class RecommendClientReviewRequest {
    /** 主案件 ID。 */
    @Positive
    private final long caseId;
    /** 操作人 Account ID（推荐人）。 */
    @Positive
    private final long actorId;
    /** APPROVE 或 REJECT。 */
    @NotBlank
    @Pattern(regexp = "^(APPROVE|REJECT)$")
    private final String recommendation;
    /** 推荐理由。 */
    @NotBlank
    @Size(max = 2000)
    private final String note;
    /** 代操作的责任 PayFac；非代操作为空。 */
    private final Long onBehalfOfPartnerId;
}
