package com.paytech.risk.decision.api.response;

import java.util.List;

import com.paytech.risk.decision.api.DecisionFlowModels.Issue;

/** 只读静态校验结果，不预留版本或执行真实决策。 */
public class ValidateFlowResponseBody {

    /**
     * 配置是否通过静态校验。
     * <li>不表示已经保存或执行。</li>
     */
    private boolean valid;

    /**
     * 可定位的静态问题。
     * <li>最多 100 项；无问题为空列表。</li>
     */
    private List<Issue> issues;

    /**
     * 问题是否因数量上限而截断。
     */
    private boolean truncated;

    /** 返回配置是否通过静态校验。 */
    public boolean getValid() { return valid; }

    /** 设置配置是否通过静态校验。 */
    public void setValid(boolean valid) { this.valid = valid; }

    /** 返回可定位的静态问题。 */
    public List<Issue> getIssues() { return issues; }

    /** 设置可定位的静态问题。 */
    public void setIssues(List<Issue> issues) { this.issues = issues; }

    /** 返回问题是否因数量上限而截断。 */
    public boolean getTruncated() { return truncated; }

    /** 设置问题是否因数量上限而截断。 */
    public void setTruncated(boolean truncated) { this.truncated = truncated; }
}
