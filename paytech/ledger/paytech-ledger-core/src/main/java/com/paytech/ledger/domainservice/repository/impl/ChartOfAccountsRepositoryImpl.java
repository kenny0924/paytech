package com.paytech.ledger.domainservice.repository.impl;

import java.util.List;

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

    /** 读取全部科目表，按科目表代码升序。 */
    @Override
    public List<ChartOfAccounts> findAll() {
        return mapper.selectAll();
    }
}
