package com.paytech.compliance.api.response;

import java.util.List;

/** 批量资格判定结果；结果顺序与请求中主体首次出现的顺序一致。 */
public class QualificationEvaluationResponseBody {

    /**
     * 判定时间
     * <li>UTC 六位微秒；有效期按该时间实时判定。</li>
     */
    private String evaluatedAt;

    /** 每个主体一条结论 */
    private List<QualificationEvaluationResultBody> results;

    /** 读取判定时间。 */
    public String getEvaluatedAt() {
        return evaluatedAt;
    }

    /** 设置判定时间。 */
    public void setEvaluatedAt(String evaluatedAt) {
        this.evaluatedAt = evaluatedAt;
    }

    /** 读取每个主体一条结论。 */
    public List<QualificationEvaluationResultBody> getResults() {
        return results;
    }

    /** 设置每个主体一条结论。 */
    public void setResults(List<QualificationEvaluationResultBody> results) {
        this.results = results;
    }
}
