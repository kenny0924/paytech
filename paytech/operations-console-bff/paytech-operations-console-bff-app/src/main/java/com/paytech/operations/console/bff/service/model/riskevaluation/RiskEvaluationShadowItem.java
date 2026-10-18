package com.paytech.operations.console.bff.service.model.riskevaluation;

import lombok.Getter;
import lombok.Setter;

/**
 * 以正式评估为基线的一条影子评估。
 */
@Getter
@Setter
public class RiskEvaluationShadowItem {
    /**
     * 影子评估 ID
     */
    private String evaluationId;

    /**
     * 影子运行 ID
     */
    private String shadowRunId;

    /**
     * 影子评估结论
     */
    private String result;

    /**
     * 结论是否与正式评估不同
     */
    private boolean decisionChanged;

    /**
     * 附加输出（认证要求）是否不同
     */
    private boolean outputChanged;

    /**
     * 变化严重程度
     */
    private String severity;
}
