package com.paytech.compliance.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** O05 核验一份材料。 */
@Getter
@AllArgsConstructor
public class ReviewClientEvidenceRequest {
    /** 主任务 ID。 */
    @Positive
    private final long caseId;
    /** 证据 ID。 */
    @Positive
    private final long evidenceId;
    /** ACCEPTED 或 REJECTED。 */
    @NotBlank
    @Pattern(regexp = "^(ACCEPTED|REJECTED)$")
    private final String outcome;
}
