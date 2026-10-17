package com.paytech.risk.decision.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.risk.decision.core.dal.dataobject.DecisionPointRuleSet;
import com.paytech.risk.decision.core.dal.mapper.DecisionFlowMapper;
import com.paytech.risk.decision.core.dal.mapper.DecisionRuleSetMapper;
import com.paytech.risk.decision.domainservice.repository.DecisionPointRuleSetRepository;

import lombok.AllArgsConstructor;

/** 复用旧版本查询 SQL，保留顺序、边界和数据映射。 */
@Repository
@AllArgsConstructor
public class DecisionPointRuleSetRepositoryImpl implements DecisionPointRuleSetRepository {
    private final DecisionFlowMapper mapper;
    private final DecisionRuleSetMapper ruleSetMapper;

    /** 只查询本表，历史解释由调用方负责。 */
    @Override
    public List<DecisionPointRuleSet> findLegacyBindings(long pointVersionId) { return List.copyOf(mapper.legacyBindings(pointVersionId)); }
    /** 批量查询保留原 SQL 的坐标与稳定顺序。 */
    @Override
    public List<DecisionPointRuleSet> findByPointVersionIds(List<Long> ids) {
        return ids.isEmpty() ? List.of() : List.copyOf(ruleSetMapper.selectPointRuleSetsByPointVersionIds(ids));
    }
    /** 沿用发布原查询，不使用配置列表的 201 条边界。 */
    @Override
    public List<DecisionPointRuleSet> findByPointVersion(long id) { return List.copyOf(ruleSetMapper.selectPointRuleSets(id)); }
}
