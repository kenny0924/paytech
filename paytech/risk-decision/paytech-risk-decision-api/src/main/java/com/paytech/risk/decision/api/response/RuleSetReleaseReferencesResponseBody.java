package com.paytech.risk.decision.api.response;

import com.paytech.foundation.web.paging.PagingResponseBody;

/** 规则集全量使用事实与筛选后的发布引用页。 */
public class RuleSetReleaseReferencesResponseBody {

    /**
     * 规则集整体根 ID。
     * <li>正 BIGINT 的十进制字符串。</li>
     */
    private String ruleSetId;

    /**
     * 全根使用标志。
     * <li>覆盖全部版本和环境，与筛选、页码及空页无关。</li>
     */
    private boolean inUse;

    /**
     * 发布引用分页。
     * <li>按发布 ID、规则版本 ID 倒序；受当前筛选条件约束。</li>
     */
    private PagingResponseBody<RuleSetReleaseReferenceResponseBody> references;

    /** 返回规则集整体根 ID。 */
    public String getRuleSetId() {
        return ruleSetId;
    }

    /** 设置规则集整体根 ID。 */
    public void setRuleSetId(String ruleSetId) {
        this.ruleSetId = ruleSetId;
    }

    /** 返回全根使用标志。 */
    public boolean isInUse() {
        return inUse;
    }

    /** 设置全根使用标志。 */
    public void setInUse(boolean inUse) {
        this.inUse = inUse;
    }

    /** 返回发布引用分页。 */
    public PagingResponseBody<RuleSetReleaseReferenceResponseBody> getReferences() {
        return references;
    }

    /** 设置发布引用分页。 */
    public void setReferences(PagingResponseBody<RuleSetReleaseReferenceResponseBody> references) {
        this.references = references;
    }
}
