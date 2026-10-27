package com.paytech.compliance.service.processor.request;

import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 重跑案件全部筛查。 */
@Getter
@AllArgsConstructor
public class RerunClientReviewScreeningRequest {
    /** 主案件 ID。 */
    @Positive
    private final long caseId;
    /** 操作人 Account ID。 */
    @Positive
    private final long actorId;
    /** 代操作的责任 PayFac；非代操作为空。 */
    private final Long onBehalfOfPartnerId;
}
