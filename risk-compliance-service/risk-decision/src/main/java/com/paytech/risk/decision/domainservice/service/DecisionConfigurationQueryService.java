package com.paytech.risk.decision.domainservice.service;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.risk.decision.domain.query.DecisionConfigurationQueries.DictionaryReferenceView;
import com.paytech.risk.decision.domain.query.DecisionConfigurationQueries.ReasonReferenceView;
import com.paytech.risk.decision.domain.query.RejectionReasonReferencesContext;
import com.paytech.risk.decision.domain.query.SubjectTagReferencesContext;

/** 已验证字典在当前快照中的精确使用事实，无执行或激活动作。 */
public interface DecisionConfigurationQueryService {
    /** 反查标签的精确保存项，并批量补充各版本活动环境。 */
    PagingResponseBody<DictionaryReferenceView> subjectTagReferences(SubjectTagReferencesContext context);
    /** 在同一分页中读取规则与流程引用，保留来源互斥字段。 */
    PagingResponseBody<ReasonReferenceView> reasonReferences(RejectionReasonReferencesContext context);
}
