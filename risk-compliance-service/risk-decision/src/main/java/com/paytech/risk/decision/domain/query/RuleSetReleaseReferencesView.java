package com.paytech.risk.decision.domain.query;

import com.paytech.foundation.web.paging.PagingResponseBody;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 规则集全量使用事实及同一查询快照中的发布引用分页。 */
@Getter
@AllArgsConstructor
public class RuleSetReleaseReferencesView {

    /** 规则集整体根 ID。 */
    private final long ruleSetId;

    /** 覆盖根下所有版本和环境的使用事实，不受筛选和分页影响。 */
    private final boolean inUse;

    /** 当前筛选下的发布引用分页。 */
    private final PagingResponseBody<RuleSetReleaseReferenceView> references;
}
