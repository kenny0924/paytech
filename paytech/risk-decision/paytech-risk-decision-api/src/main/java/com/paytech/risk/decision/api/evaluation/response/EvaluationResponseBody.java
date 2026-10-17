package com.paytech.risk.decision.api.evaluation.response;

import java.util.Map;

/** 一次评估的结论及其发布与流程坐标。 */
public class EvaluationResponseBody {

    /** 评估 ID，十进制字符串。 */
    private String evaluationId;

    /** 风控点编码。 */
    private String pointCode;

    /**
     * 业务身份，如支付尝试 ID。
     * <li>影子评估为其基线评估的业务身份。</li>
     */
    private String businessKey;

    /** 评估模式：PRIMARY 正式、SHADOW 影子。 */
    private String mode;

    /** 结论：ALLOW、REVIEW、DENY；技术终态 FAILED、TIMED_OUT。 */
    private String result;

    /**
     * 到达结束节点的原因码。
     * <li>放行、技术失败或没有记录流程路径时为空。</li>
     */
    private String reasonCode;

    /**
     * 到达结束节点的原因文案。
     * <li>为空条件同原因码。</li>
     */
    private String reasonMessage;

    /**
     * 附加输出，如 AUTHENTICATION_REQUIREMENT。
     * <li>仅 ALLOW 时有值，其他结论为空对象；正式评估取已保存的值，影子评估取所到结束节点的配置。</li>
     */
    private Map<String, String> outputs;

    /**
     * 失败代码：EVALUATION_ERROR、FEATURE_ERROR、EVALUATION_TIMEOUT。
     * <li>仅 FAILED、TIMED_OUT 有值。</li>
     */
    private String failureCode;

    /** 执行发布 ID，十进制字符串。 */
    private String releaseId;

    /** 发布环境 Code。 */
    private String environment;

    /** 决策流 ID，十进制字符串。 */
    private String flowId;

    /** 决策流编码。 */
    private String flowCode;

    /** 决策流版本 ID，十进制字符串。 */
    private String flowVersionId;

    /** 决策流版本号。 */
    private int flowVersionNo;

    /** 评估时间，UTC ISO-8601。 */
    private String evaluationTime;

    /** 执行耗时毫秒。 */
    private int elapsedMillis;

    /**
     * 基线评估 ID，十进制字符串。
     * <li>仅影子评估有值。</li>
     */
    private String baselineEvaluationId;

    /**
     * 影子批次 ID，十进制字符串。
     * <li>仅影子评估有值。</li>
     */
    private String shadowRunId;

    /**
     * 是否记录了流程路径。
     * <li>本功能上线前的评估为 false，原因码与影子输出因此为空。</li>
     */
    private boolean pathAvailable;

    /** 返回评估 ID。 */
    public String getEvaluationId() { return evaluationId; }

    /** 设置评估 ID。 */
    public void setEvaluationId(String evaluationId) { this.evaluationId = evaluationId; }

    /** 返回风控点编码。 */
    public String getPointCode() { return pointCode; }

    /** 设置风控点编码。 */
    public void setPointCode(String pointCode) { this.pointCode = pointCode; }

    /** 返回业务身份。 */
    public String getBusinessKey() { return businessKey; }

    /** 设置业务身份。 */
    public void setBusinessKey(String businessKey) { this.businessKey = businessKey; }

    /** 返回评估模式。 */
    public String getMode() { return mode; }

    /** 设置评估模式。 */
    public void setMode(String mode) { this.mode = mode; }

    /** 返回结论。 */
    public String getResult() { return result; }

    /** 设置结论。 */
    public void setResult(String result) { this.result = result; }

    /** 返回到达结束节点的原因码。 */
    public String getReasonCode() { return reasonCode; }

    /** 设置到达结束节点的原因码。 */
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }

    /** 返回到达结束节点的原因文案。 */
    public String getReasonMessage() { return reasonMessage; }

    /** 设置到达结束节点的原因文案。 */
    public void setReasonMessage(String reasonMessage) { this.reasonMessage = reasonMessage; }

    /** 返回附加输出。 */
    public Map<String, String> getOutputs() { return outputs; }

    /** 设置附加输出。 */
    public void setOutputs(Map<String, String> outputs) { this.outputs = outputs; }

    /** 返回失败代码。 */
    public String getFailureCode() { return failureCode; }

    /** 设置失败代码。 */
    public void setFailureCode(String failureCode) { this.failureCode = failureCode; }

    /** 返回执行发布 ID。 */
    public String getReleaseId() { return releaseId; }

    /** 设置执行发布 ID。 */
    public void setReleaseId(String releaseId) { this.releaseId = releaseId; }

    /** 返回发布环境 Code。 */
    public String getEnvironment() { return environment; }

    /** 设置发布环境 Code。 */
    public void setEnvironment(String environment) { this.environment = environment; }

    /** 返回决策流 ID。 */
    public String getFlowId() { return flowId; }

    /** 设置决策流 ID。 */
    public void setFlowId(String flowId) { this.flowId = flowId; }

    /** 返回决策流编码。 */
    public String getFlowCode() { return flowCode; }

    /** 设置决策流编码。 */
    public void setFlowCode(String flowCode) { this.flowCode = flowCode; }

    /** 返回决策流版本 ID。 */
    public String getFlowVersionId() { return flowVersionId; }

    /** 设置决策流版本 ID。 */
    public void setFlowVersionId(String flowVersionId) { this.flowVersionId = flowVersionId; }

    /** 返回决策流版本号。 */
    public int getFlowVersionNo() { return flowVersionNo; }

    /** 设置决策流版本号。 */
    public void setFlowVersionNo(int flowVersionNo) { this.flowVersionNo = flowVersionNo; }

    /** 返回评估时间。 */
    public String getEvaluationTime() { return evaluationTime; }

    /** 设置评估时间。 */
    public void setEvaluationTime(String evaluationTime) { this.evaluationTime = evaluationTime; }

    /** 返回执行耗时毫秒。 */
    public int getElapsedMillis() { return elapsedMillis; }

    /** 设置执行耗时毫秒。 */
    public void setElapsedMillis(int elapsedMillis) { this.elapsedMillis = elapsedMillis; }

    /** 返回基线评估 ID。 */
    public String getBaselineEvaluationId() { return baselineEvaluationId; }

    /** 设置基线评估 ID。 */
    public void setBaselineEvaluationId(String baselineEvaluationId) { this.baselineEvaluationId = baselineEvaluationId; }

    /** 返回影子批次 ID。 */
    public String getShadowRunId() { return shadowRunId; }

    /** 设置影子批次 ID。 */
    public void setShadowRunId(String shadowRunId) { this.shadowRunId = shadowRunId; }

    /** 返回是否记录了流程路径。 */
    public boolean isPathAvailable() { return pathAvailable; }

    /** 设置是否记录了流程路径。 */
    public void setPathAvailable(boolean pathAvailable) { this.pathAvailable = pathAvailable; }
}
