package com.paytech.risk.api.evaluation.response;

/** 支付尝试风控评估结果；同一支付尝试重放返回原值。 */
public class PaymentRiskAssessmentResponseBody {

    /**
     * 交易评估 ID
     * <li>正整数十进制字符串；Payment 保存到风控 Action。</li>
     */
    private String assessmentId;

    /** 正式评估 ID，正整数十进制字符串。 */
    private String evaluationId;

    /**
     * 风控结果
     * <li>ALLOW、REVIEW、DENY、FAILED 或 TIMED_OUT；只有 ALLOW 可以继续支付。</li>
     */
    private String riskResult;

    /**
     * 认证要求
     * <li>仅 ALLOW 非空：NOT_REQUIRED 或 THREE_DS_REQUIRED；一期不返回 CHALLENGE_REQUIRED。</li>
     */
    private String authenticationRequirement;

    /** 读取交易评估 ID。 */
    public String getAssessmentId() { return assessmentId; }

    /** 设置交易评估 ID。 */
    public void setAssessmentId(String assessmentId) { this.assessmentId = assessmentId; }

    /** 读取正式评估 ID。 */
    public String getEvaluationId() { return evaluationId; }

    /** 设置正式评估 ID。 */
    public void setEvaluationId(String evaluationId) { this.evaluationId = evaluationId; }

    /** 读取风控结果。 */
    public String getRiskResult() { return riskResult; }

    /** 设置风控结果。 */
    public void setRiskResult(String riskResult) { this.riskResult = riskResult; }

    /** 读取认证要求。 */
    public String getAuthenticationRequirement() { return authenticationRequirement; }

    /** 设置认证要求。 */
    public void setAuthenticationRequirement(String authenticationRequirement) { this.authenticationRequirement = authenticationRequirement; }
}
