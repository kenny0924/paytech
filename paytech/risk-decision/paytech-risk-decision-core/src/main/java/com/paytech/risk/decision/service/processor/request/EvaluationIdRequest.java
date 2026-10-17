package com.paytech.risk.decision.service.processor.request;

import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按评估 ID 查询的 Owner 输入，由公共模板执行基础校验。 */
@Getter
@AllArgsConstructor
public class EvaluationIdRequest {
    /** 评估 ID，正整数。 */
    @Min(1)
    private final long evaluationId;
}
