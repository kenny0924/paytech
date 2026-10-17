package com.paytech.ledger.domainservice.repository.impl;

import org.springframework.stereotype.Repository;

import com.paytech.ledger.core.dal.dataobject.ChartOfAccounts;
import com.paytech.ledger.core.dal.mapper.ChartOfAccountsMapper;
import com.paytech.ledger.domainservice.repository.ChartOfAccountsRepository;

import lombok.AllArgsConstructor;

/** ChartOfAccountsRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class ChartOfAccountsRepositoryImpl implements ChartOfAccountsRepository {
    private final ChartOfAccountsMapper mapper;

    /** 按科目表代码读取，不存在返回空。 */
    @Override
    public ChartOfAccounts findByChartCode(String chartCode) {
        return mapper.selectByChartCode(chartCode);
    }
}
