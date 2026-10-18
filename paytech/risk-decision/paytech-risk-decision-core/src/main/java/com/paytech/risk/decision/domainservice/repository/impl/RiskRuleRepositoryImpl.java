package com.paytech.risk.decision.domainservice.repository.impl;

import java.util.List;
import org.springframework.stereotype.Repository;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.decision.core.dal.dataobject.RiskRule;
import com.paytech.risk.decision.core.dal.mapper.DecisionRuleSetMapper;
import com.paytech.risk.decision.domainservice.repository.RiskRuleRepository;
import lombok.AllArgsConstructor;

/** RiskRule 批量插入并核对受影响行数。 */
@Repository
@AllArgsConstructor
public class RiskRuleRepositoryImpl implements RiskRuleRepository {
    private final DecisionRuleSetMapper mapper;

    /** 一次写入本表，失败交由用例事务整体回滚。 */
    @Override
    public void insertAll(List<RiskRule> rows) {
        if (!rows.isEmpty() && mapper.insertRules(rows) != rows.size())
            throw new ErrorException("插入 RiskRule 受影响行数不符");
    }
}
