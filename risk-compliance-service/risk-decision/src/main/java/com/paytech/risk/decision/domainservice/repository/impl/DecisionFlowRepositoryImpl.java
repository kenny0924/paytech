package com.paytech.risk.decision.domainservice.repository.impl;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.decision.core.dal.dataobject.RuleRejectReason;
import com.paytech.risk.decision.core.dal.dataobject.RuleSet;
import com.paytech.risk.decision.core.dal.dataobject.RuleSetVersion;
import com.paytech.risk.decision.core.dal.mapper.DecisionDictionaryMapper;
import com.paytech.risk.decision.core.dal.mapper.DecisionFlowMapper;
import com.paytech.risk.decision.core.dal.mapper.DecisionRuleSetMapper;
import com.paytech.risk.decision.domainservice.repository.DecisionFlowRepository;

import lombok.AllArgsConstructor;

/** 既有引用资格读取与共享锁查询，保持管理入口的原锁顺序。 */
@Repository
@AllArgsConstructor
public class DecisionFlowRepositoryImpl implements DecisionFlowRepository {
    private final DecisionFlowMapper mapper;
    private final DecisionRuleSetMapper rules;
    private final DecisionDictionaryMapper dictionaries;

    @Override
    public Optional<RuleSet> ruleRoot(long versionId) {
        RuleSetVersion version = rules.selectRuleSetVersion(versionId);
        if (version == null) return Optional.empty();
        return Optional.ofNullable(rules.selectRuleSetByCode(version.decisionPointId(), version.ruleSetCode()));
    }

    @Override
    public RuleSet shareRuleRoot(long rootId) {
        RuleSet root = mapper.shareRuleSet(rootId);
        if (root == null) throw new ErrorException("已定位的规则集根缺失");
        return root;
    }

    @Override
    public Optional<RuleSetVersion> ruleVersion(long id, boolean lock) {
        return Optional.ofNullable(lock ? mapper.shareRuleVersion(id) : rules.selectRuleSetVersion(id));
    }

    @Override
    public int ruleCount(long versionId) { return mapper.ruleCount(versionId); }

    @Override
    public Optional<RuleRejectReason> reason(String code, boolean lock) {
        return Optional.ofNullable(dictionaries.findReason(code, lock ? "SHARE" : null));
    }
}
