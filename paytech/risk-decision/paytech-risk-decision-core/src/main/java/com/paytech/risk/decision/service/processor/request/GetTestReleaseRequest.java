package com.paytech.risk.decision.service.processor.request;

import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 发布接口的 Owner 输入，由公共模板执行基础校验。 */
@Getter
@AllArgsConstructor
public class GetTestReleaseRequest {

    /** 发布身份。 */
    @Positive
    private final long releaseId;
}
