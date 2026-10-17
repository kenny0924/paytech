package com.paytech.risk.decision.domainservice.repository.impl;

import java.util.List;
import org.springframework.stereotype.Repository;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.decision.core.dal.dataobject.RuleOperand;
import com.paytech.risk.decision.core.dal.mapper.DecisionRuleSetMapper;
import com.paytech.risk.decision.domainservice.repository.RuleOperandRepository;
import lombok.AllArgsConstructor;

/** RuleOperand 批量插入并核对受影响行数。 */
@Repository
@AllArgsConstructor
public class RuleOperandRepositoryImpl implements RuleOperandRepository {
    private final DecisionRuleSetMapper mapper;

    /** 一次写入本表，失败交由用例事务整体回滚。 */
    @Override
    public void insertAll(List<RuleOperand> rows) {
        if (!rows.isEmpty() && mapper.insertOperands(rows) != rows.size())
            throw new ErrorException("插入 RuleOperand 受影响行数不符");
    }
}
