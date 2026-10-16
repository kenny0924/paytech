package com.paytech.finance.accounting.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.finance.accounting.core.dal.dataobject.CloseAccountRule;
import com.paytech.finance.accounting.core.dal.mapper.CloseAccountRuleMapper;
import com.paytech.finance.accounting.domainservice.repository.CloseAccountRuleRepository;

import lombok.AllArgsConstructor;

/** CloseAccountRuleRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class CloseAccountRuleRepositoryImpl implements CloseAccountRuleRepository {
    private final CloseAccountRuleMapper mapper;

    /** 读取指定时点生效的结转规则，不存在返回空。 */
    @Override
    public CloseAccountRule findEffective(long chartId, long sourceAccountId, Instant at) {
        return mapper.selectEffective(chartId, sourceAccountId, at);
    }

    /** 读取指定时点在科目表内生效的全部结转规则。 */
    @Override
    public List<CloseAccountRule> findAllEffective(long chartId, Instant at) {
        return mapper.selectAllEffective(chartId, at);
    }
}
