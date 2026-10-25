package com.paytech.compliance.service.processor.request;

import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一个角色资格的状态变更历史查询。 */
@Getter
@AllArgsConstructor
public class QualificationHistoryRequest {
    /** 角色资格 ID。 */
    @Positive
    private final long qualificationId;
}
