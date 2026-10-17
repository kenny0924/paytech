package com.paytech.risk.decision.domainservice.repository;

import java.util.Optional;

import com.paytech.risk.decision.core.dal.dataobject.RuleRejectReason;
import com.paytech.risk.decision.core.dal.dataobject.RuleSet;
import com.paytech.risk.decision.core.dal.dataobject.RuleSetVersion;

/** 已有规则引用资格校验的持久化边界；流程查询已使用所属表 Repository。 */
public interface DecisionFlowRepository {
    /** 先解析不可变版本的根，再按根 ID 排序获取共享锁。 */
    Optional<RuleSet> ruleRoot(long versionId);
    RuleSet shareRuleRoot(long rootId);
    Optional<RuleSetVersion> ruleVersion(long id, boolean lock);
    int ruleCount(long versionId);
    Optional<RuleRejectReason> reason(String code, boolean lock);
}
