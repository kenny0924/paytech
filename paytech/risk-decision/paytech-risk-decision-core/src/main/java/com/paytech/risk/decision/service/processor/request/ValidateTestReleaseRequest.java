package com.paytech.risk.decision.service.processor.request;

import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 发布接口的 Owner 输入，由公共模板执行基础校验。 */
@Getter
@AllArgsConstructor
public class ValidateTestReleaseRequest {

    /** 可信操作人身份。 */
    @Positive
    private final long actorAccountId;

    /** 需要开始或恢复校验的发布身份。 */
    @Positive
    private final long releaseId;

    /** 原校验开始前的聚合版本。 */
    @Positive
    private final int expectedVersion;
}
