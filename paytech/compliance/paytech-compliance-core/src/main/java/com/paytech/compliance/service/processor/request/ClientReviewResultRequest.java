package com.paytech.compliance.service.processor.request;

import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按 CCT 申请读取最近一次审核结果。 */
@Getter
@AllArgsConstructor
public class ClientReviewResultRequest {
    /** CCT 申请 ID。 */
    @Positive
    private final long applicationId;
}
