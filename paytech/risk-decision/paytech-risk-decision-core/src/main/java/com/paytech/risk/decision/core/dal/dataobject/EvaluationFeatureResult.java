package com.paytech.risk.decision.core.dal.dataobject;

import com.paytech.risk.decision.domain.enums.DecisionValueType;
import com.paytech.risk.decision.domain.enums.TechnicalStatus;
import java.math.BigDecimal;
import java.time.Instant;

import lombok.Data;

/** 评估中实际执行的 Feature 结果（t_evaluation_feature_result）。 */
@Data
public class EvaluationFeatureResult {
    /** 结果 ID。 */
    private long resultId;

    /** 评估 ID。 */
    private long evaluationId;

    /** Feature Version ID。 */
    private long featureVersionId;

    /** 执行顺序，从 1 开始。 */
    private int executionSequence;

    /** 技术状态。 */
    private TechnicalStatus technicalStatus;

    /** 结果值类型。 */
    private DecisionValueType valueType;

    /** STRING 结果。 */
    private String stringValue;

    /** NUMBER 结果。 */
    private BigDecimal numberValue;

    /** BOOLEAN 结果。 */
    private Boolean booleanValue;

    /** DATETIME 结果。 */
    private Instant datetimeValue;

    /** DURATION 结果，毫秒。 */
    private Long durationValue;

    /** 执行耗时毫秒。 */
    private int elapsedMillis;

    /** 失败代码。 */
    private String failureCode;

    /** 创建 UTC 时间。 */
    private Instant createTime;
}
