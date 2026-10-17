package com.paytech.risk.decision.service.processor.request;

import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 执行发布列表的 Owner 输入，由公共模板执行基础校验。 */
@Getter
@AllArgsConstructor
public class ListExecutionReleasesRequest {

    /** 页码。 */
    @Min(1)
    private final int page;

    /** 每页数量。 */
    @Min(1) @Max(200)
    private final int size;

    /** 决策点编码，必填。 */
    @NotNull
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}")
    private final String pointCode;

    /** 可选发布状态。 */
    private final DecisionReleaseStatus status;
}
