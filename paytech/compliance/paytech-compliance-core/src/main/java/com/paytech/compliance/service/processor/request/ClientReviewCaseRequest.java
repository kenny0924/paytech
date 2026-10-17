package com.paytech.risk.compliance.service.processor.request;

import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按主案件 ID 读取审核任务。 */
@Getter
@AllArgsConstructor
public class ClientReviewCaseRequest {
    /** 主案件 ID。 */
    @Positive
    private final long caseId;
}
