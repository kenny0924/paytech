package com.paytech.compliance.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 豁免一项材料要求。 */
@Getter
@AllArgsConstructor
public class WaiveClientReviewRequirementRequest {
    /** 主案件 ID。 */
    @Positive
    private final long caseId;
    /** 要求 ID。 */
    @Positive
    private final long requirementId;
    /** 操作人 Account ID。 */
    @Positive
    private final long actorId;
    /** 豁免原因码。 */
    @NotBlank
    private final String reasonCode;
    /** 豁免说明。 */
    @NotBlank
    @Size(max = 1000)
    private final String note;
    /** 代操作的责任 PayFac；非代操作为空。 */
    private final Long onBehalfOfPartnerId;
}
