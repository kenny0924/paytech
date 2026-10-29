package com.paytech.risk.control.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.risk.control.core.dal.dataobject.UnderwritingRuleThreshold;
import com.paytech.risk.control.core.dal.mapper.UnderwritingRuleThresholdMapper;
import com.paytech.risk.control.domainservice.repository.UnderwritingRuleThresholdRepository;

import lombok.AllArgsConstructor;

/** 自动承保币种阈值读取。 */
@Repository
@AllArgsConstructor
public class UnderwritingRuleThresholdRepositoryImpl implements UnderwritingRuleThresholdRepository {
    private final UnderwritingRuleThresholdMapper mapper;

    /** 读取某规则版本的全部阈值。 */
    @Override
    public List<UnderwritingRuleThreshold> findByRuleVersionId(long ruleVersionId) {
        return mapper.selectByRuleVersionId(ruleVersionId);
    }
}
