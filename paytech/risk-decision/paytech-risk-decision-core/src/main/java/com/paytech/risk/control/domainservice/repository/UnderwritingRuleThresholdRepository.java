package com.paytech.risk.control.domainservice.repository;

import java.util.List;

import com.paytech.risk.control.core.dal.dataobject.UnderwritingRuleThreshold;

/** 自动承保币种阈值的单表只读边界。 */
public interface UnderwritingRuleThresholdRepository {
    /** 读取某规则版本的全部阈值。 */
    List<UnderwritingRuleThreshold> findByRuleVersionId(long ruleVersionId);
}
