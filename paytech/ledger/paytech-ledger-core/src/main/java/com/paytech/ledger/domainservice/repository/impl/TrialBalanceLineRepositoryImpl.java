package com.paytech.ledger.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.ledger.core.dal.dataobject.TrialBalanceLine;
import com.paytech.ledger.core.dal.mapper.TrialBalanceLineMapper;
import com.paytech.ledger.domain.view.AccountBalanceView;
import com.paytech.ledger.domainservice.repository.TrialBalanceLineRepository;

import lombok.AllArgsConstructor;

/** TrialBalanceLineRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class TrialBalanceLineRepositoryImpl implements TrialBalanceLineRepository {
    private final TrialBalanceLineMapper mapper;

    /** 批量插入试算行；空列表不访问数据库。 */
    @Override
    public void insertBatch(List<TrialBalanceLine> lines) {
        if (!lines.isEmpty()) {
            mapper.insertBatch(lines);
        }
    }

    /** 读取一次试算的全部行及科目。 */
    @Override
    public List<AccountBalanceView> findViews(long runId) {
        return mapper.selectViews(runId);
    }
}
