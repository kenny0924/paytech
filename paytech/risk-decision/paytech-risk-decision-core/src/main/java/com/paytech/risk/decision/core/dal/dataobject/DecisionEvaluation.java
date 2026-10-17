package com.paytech.risk.decision.core.dal.dataobject;

import java.time.Instant;

import com.paytech.risk.decision.domain.enums.DecisionValue;
import com.paytech.risk.decision.domain.enums.EvaluationMode;
import com.paytech.risk.decision.domain.enums.TechnicalStatus;

import lombok.Data;

/** 一次正式、影子或重放评估的执行事实（t_decision_evaluation）。 */
@Data
public class DecisionEvaluation {
    /** 评估 ID。 */
    private long evaluationId;

    /**
     * 评估请求身份
     * <li>正式支付评估为 PAYMENT_ATTEMPT_RISK:{paymentAttemptId}，正式结账评估为 CHECKOUT_PREPARE:{orderId}；影子评估为 SHADOW:{shadowRunId}:{baselineEvaluationId}。</li>
     */
    private String evaluationRequestId;

    /** 决策点 ID。 */
    private long decisionPointId;

    /** 决策点版本 ID。 */
    private long pointVersionId;

    /** 执行所用发布 ID。 */
    private long releaseId;

    /** 评估模式。 */
    private EvaluationMode evaluationMode;

    /** 评估时间锚点；影子评估等于基线评估时间。 */
    private Instant evaluationTime;

    /** 技术状态。 */
    private TechnicalStatus technicalStatus;

    /** 规则或默认决定；技术失败为空。 */
    private DecisionValue proposedDecision;

    /** 正式业务决定；一期没有系统义务，与建议决定相同。 */
    private DecisionValue businessDecision;

    /** 决定调整原因；一期恒为空。 */
    private String decisionAdjustmentReasonCode;

    /** 是否使用了决策点默认决定。 */
    private boolean defaultDecisionUsedFlag;

    /** 开始 UTC 时间。 */
    private Instant startedTime;

    /** 完成 UTC 时间。 */
    private Instant completedTime;

    /** 执行耗时毫秒。 */
    private int elapsedMillis;

    /** 输入快照 SHA-256。 */
    private String inputSnapshotHash;

    /** 失败代码；仅技术失败或超时非空。 */
    private String failureCode;

    /** 影子批次 ID；仅 SHADOW 非空。 */
    private Long shadowRunId;

    /** 基线评估 ID；仅 SHADOW 非空。 */
    private Long baselineEvaluationId;

    /** 决定是否与基线不同；仅 SHADOW 非空。 */
    private Boolean decisionChangedFlag;

    /** 认证要求等动作结果是否与基线不同；仅 SHADOW 非空。 */
    private Boolean actionChangedFlag;

    /** 比较严重程度；仅 SHADOW 非空。 */
    private String comparisonSeverity;

    /** 比较 UTC 时间；仅 SHADOW 非空。 */
    private Instant comparedTime;

    /** 创建 UTC 时间。 */
    private Instant createTime;

    /** 更新 UTC 时间。 */
    private Instant updateTime;
}
