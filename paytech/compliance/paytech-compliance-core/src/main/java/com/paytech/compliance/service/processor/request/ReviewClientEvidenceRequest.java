package com.paytech.compliance.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** O05 核验一份材料。 */
@Getter
@AllArgsConstructor
public class ReviewClientEvidenceRequest {
    /** 主案件 ID。 */
    @Positive
    private final long caseId;
    /** 证据 ID。 */
    @Positive
    private final long evidenceId;
    /** 操作人 Account ID。 */
    @Positive
    private final long actorId;
    /** ACCEPTED 或 REJECTED。 */
    @NotBlank
    @Pattern(regexp = "^(ACCEPTED|REJECTED)$")
    private final String outcome;
    /** 拒绝原因码；接受为空。 */
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,63}$")
    private final String rejectReasonCode;
    /** 内部备注。 */
    @Size(max = 1000)
    private final String note;
    /** 代操作的责任 PayFac；非代操作为空。 */
    private final Long onBehalfOfPartnerId;
}
