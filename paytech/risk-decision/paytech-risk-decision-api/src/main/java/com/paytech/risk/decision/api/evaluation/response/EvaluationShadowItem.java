package com.paytech.risk.decision.api.evaluation.response;

/** 以某正式评估为基线的一个影子评估的比较结果。 */
public class EvaluationShadowItem {

    /** 影子评估 ID，十进制字符串；可单独查询结论与解释。 */
    private String evaluationId;

    /** 影子批次 ID，十进制字符串。 */
    private String shadowRunId;

    /** 影子结论或技术状态。 */
    private String result;

    /** 结论是否与基线不同。 */
    private boolean decisionChanged;

    /** 附加输出（认证要求）是否与基线不同。 */
    private boolean outputChanged;

    /** 变化严重程度：NONE、LOW、MEDIUM、HIGH、CRITICAL。 */
    private String severity;

    /** 返回影子评估 ID。 */
    public String getEvaluationId() { return evaluationId; }

    /** 设置影子评估 ID。 */
    public void setEvaluationId(String evaluationId) { this.evaluationId = evaluationId; }

    /** 返回影子批次 ID。 */
    public String getShadowRunId() { return shadowRunId; }

    /** 设置影子批次 ID。 */
    public void setShadowRunId(String shadowRunId) { this.shadowRunId = shadowRunId; }

    /** 返回影子结论或技术状态。 */
    public String getResult() { return result; }

    /** 设置影子结论或技术状态。 */
    public void setResult(String result) { this.result = result; }

    /** 返回结论是否与基线不同。 */
    public boolean isDecisionChanged() { return decisionChanged; }

    /** 设置结论是否与基线不同。 */
    public void setDecisionChanged(boolean decisionChanged) { this.decisionChanged = decisionChanged; }

    /** 返回附加输出。 */
    public boolean isOutputChanged() { return outputChanged; }

    /** 设置附加输出。 */
    public void setOutputChanged(boolean outputChanged) { this.outputChanged = outputChanged; }

    /** 返回变化严重程度。 */
    public String getSeverity() { return severity; }

    /** 设置变化严重程度。 */
    public void setSeverity(String severity) { this.severity = severity; }
}
