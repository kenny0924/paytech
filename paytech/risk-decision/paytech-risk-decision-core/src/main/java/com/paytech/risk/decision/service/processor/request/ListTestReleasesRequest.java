package com.paytech.risk.decision.service.processor.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 发布接口的 Owner 输入，由公共模板执行基础校验。 */
@Getter
@AllArgsConstructor
public class ListTestReleasesRequest {

    /** 页码。 */
    @Min(1)
    private final int page;

    /** 每页数量。 */
    @Min(1) @Max(200)
    private final int size;

    /** 可选决策点编码。 */
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}")
    private final String pointCode;

    /** 可选 Candidate 状态。 */
    private final DecisionReleaseStatus status;
}
