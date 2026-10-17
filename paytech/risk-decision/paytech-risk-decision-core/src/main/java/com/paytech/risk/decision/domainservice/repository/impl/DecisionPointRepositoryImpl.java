package com.paytech.risk.decision.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.risk.decision.core.dal.dataobject.DecisionPoint;
import com.paytech.risk.decision.core.dal.mapper.DecisionRuleSetMapper;
import com.paytech.risk.decision.domainservice.repository.DecisionPointRepository;

import lombok.AllArgsConstructor;

/** 只查询本表的准确事实。 */
@Repository
@AllArgsConstructor
public class DecisionPointRepositoryImpl implements DecisionPointRepository {
    private final DecisionRuleSetMapper mapper;
    /** 批量查询保留原 SQL 的坐标与稳定顺序。 */
    @Override
    public List<DecisionPoint> findByIds(List<Long> ids) {
        return ids.isEmpty() ? List.of() : List.copyOf(mapper.selectDecisionPointsByIds(ids));
    }
}
