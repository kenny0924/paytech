package com.paytech.compliance.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 处置一条筛查命中。 */
@Getter
@AllArgsConstructor
public class ResolveScreeningHitRequest {
    /** 主案件 ID。 */
    @Positive
    private final long caseId;
    /** 命中 ID。 */
    @Positive
    private final long hitId;
    /** 操作人 Account ID。 */
    @Positive
    private final long actorId;
    /** 处置结论代码。 */
    @NotBlank
    private final String resolution;
    /** 处置原因码。 */
    @NotBlank
    private final String reasonCode;
    /** 内部备注。 */
    @Size(max = 1000)
    private final String note;
    /** 代操作的责任 PayFac；非代操作为空。 */
    private final Long onBehalfOfPartnerId;
}
