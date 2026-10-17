package com.paytech.risk.api.evaluation.response;

import java.util.Map;

/** 统一风控评估结论。 */
public class EvaluateDecisionResponseBody {
    /** 评估 ID，十进制字符串；调用方保存以便追溯。 */
    private String evaluationId;

    /** ALLOW、REVIEW、DENY；技术终态 FAILED、TIMED_OUT。 */
    private String result;

    /**
     * 附加输出
     * <li>ALLOW 时为该风控点声明的全部输出（如支付的 AUTHENTICATION_REQUIREMENT），其他结论为空对象。</li>
     */
    private Map<String, String> outputs;

    /** 返回评估 ID。 */
    public String getEvaluationId() { return evaluationId; }

    /** 设置评估 ID。 */
    public void setEvaluationId(String evaluationId) { this.evaluationId = evaluationId; }

    /** 返回评估结论。 */
    public String getResult() { return result; }

    /** 设置评估结论。 */
    public void setResult(String result) { this.result = result; }

    /** 返回附加输出。 */
    public Map<String, String> getOutputs() { return outputs; }

    /** 设置附加输出。 */
    public void setOutputs(Map<String, String> outputs) { this.outputs = outputs; }
}
