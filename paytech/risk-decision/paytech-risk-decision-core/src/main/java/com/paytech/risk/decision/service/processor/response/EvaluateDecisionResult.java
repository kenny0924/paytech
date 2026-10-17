package com.paytech.risk.decision.service.processor.response;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 统一风控评估结论；同一业务身份重放返回原值。 */
@Getter
@AllArgsConstructor
public class EvaluateDecisionResult {
    /** 正式评估 ID。 */
    private final long evaluationId;

    /** 结论 Code：ALLOW、REVIEW、DENY、FAILED 或 TIMED_OUT。 */
    private final String result;

    /** 附加输出；仅 ALLOW 非空。 */
    private final Map<String, String> outputs;
}
