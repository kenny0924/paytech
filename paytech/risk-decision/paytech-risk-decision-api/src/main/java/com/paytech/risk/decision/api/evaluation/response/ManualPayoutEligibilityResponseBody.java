package com.paytech.risk.decision.api.evaluation.response;

import java.util.List;

/** 一次不可变资格评估的风控决定与证据坐标。 */
public class ManualPayoutEligibilityResponseBody {

    /** 评估所属 Assessment ID；同一命令重放返回原值。 */
    private long assessmentId;

    /** 本次 Evaluation ID；不同检查点必须使用新值，同一命令重放返回原值。 */
    private long evaluationId;

    /** 风控决定 Code。 */
    private String decision;

    /** 按 Code 排序的拒绝或复核原因。 */
    private List<String> reasonCodes;

    /** 本次命中的完整 Control 列表。 */
    private List<String> matchedControls;

    /**
     * Client 最低保证金要求
     * <li>币种主单位十进制字符串；LOCAL_PARTNER 恒为 null。</li>
     */
    private String requiredAmount;

    /**
     * 最低保证金币种
     * <li>LOCAL_PARTNER 恒为 null。</li>
     */
    private String requiredCurrency;

    /** 本次评估使用的 Control 集合摘要。 */
    private String controlSetHash;

    /** 读取 Assessment ID。 */
    public long getAssessmentId() { return assessmentId; }

    /** 设置 Assessment ID。 */
    public void setAssessmentId(long assessmentId) { this.assessmentId = assessmentId; }

    /** 读取 Evaluation ID。 */
    public long getEvaluationId() { return evaluationId; }

    /** 设置 Evaluation ID。 */
    public void setEvaluationId(long evaluationId) { this.evaluationId = evaluationId; }

    /** 读取风控决定 Code。 */
    public String getDecision() { return decision; }

    /** 设置风控决定 Code。 */
    public void setDecision(String decision) { this.decision = decision; }

    /** 读取原因列表。 */
    public List<String> getReasonCodes() { return reasonCodes; }

    /** 设置原因列表。 */
    public void setReasonCodes(List<String> reasonCodes) { this.reasonCodes = reasonCodes; }

    /** 读取命中的 Control 列表。 */
    public List<String> getMatchedControls() { return matchedControls; }

    /** 设置命中的 Control 列表。 */
    public void setMatchedControls(List<String> matchedControls) { this.matchedControls = matchedControls; }

    /** 读取最低保证金要求。 */
    public String getRequiredAmount() { return requiredAmount; }

    /** 设置最低保证金要求。 */
    public void setRequiredAmount(String requiredAmount) { this.requiredAmount = requiredAmount; }

    /** 读取最低保证金币种。 */
    public String getRequiredCurrency() { return requiredCurrency; }

    /** 设置最低保证金币种。 */
    public void setRequiredCurrency(String requiredCurrency) { this.requiredCurrency = requiredCurrency; }

    /** 读取 Control 集合摘要。 */
    public String getControlSetHash() { return controlSetHash; }

    /** 设置 Control 集合摘要。 */
    public void setControlSetHash(String controlSetHash) { this.controlSetHash = controlSetHash; }
}
