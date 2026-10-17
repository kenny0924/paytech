package com.paytech.risk.decision.domainservice.service;

import com.paytech.risk.decision.core.dal.dataobject.RuleRejectReason;
import com.paytech.risk.decision.core.dal.dataobject.RuleSubjectTag;
import com.paytech.risk.decision.domain.dictionary.RejectionReasonChangeContext;
import com.paytech.risk.decision.domain.dictionary.SubjectTagChangeContext;
import com.paytech.risk.decision.domain.rule.DecisionRuleSetAggregate;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.NormalizedRuleSet;

/** 当前目录维护与首次保存资格，历史回读不依赖此服务。 */
public interface DecisionDictionaryService {
    /** 在调用方事务内保存已完成创建资格检查的原因，不重复查询或校验输入。 */
    void createRejectionReason(RuleRejectReason reason);

    /** 保存已完成创建资格检查的主体标签。 */
    void createSubjectTag(RuleSubjectTag tag);
    /** 保存原因的新内容或状态，不重复加载已经锁定的对象。 */
    void saveRejectionReasonChange(RejectionReasonChangeContext context);
    /** 保存标签的新内容或状态，不重复加载已经锁定的对象。 */
    void saveSubjectTagChange(SubjectTagChangeContext context);

    /** 从保存基线识别精确沿用项，仅对新增选择持有字典共享锁并要求启用。 */
    void validateSelections(NormalizedRuleSet draft, DecisionRuleSetAggregate baseline);
}
