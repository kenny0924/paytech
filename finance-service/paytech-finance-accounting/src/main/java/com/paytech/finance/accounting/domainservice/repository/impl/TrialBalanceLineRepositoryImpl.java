package com.paytech.finance.accounting.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.finance.accounting.core.dal.dataobject.TrialBalanceLine;
import com.paytech.finance.accounting.core.dal.mapper.TrialBalanceLineMapper;
import com.paytech.finance.accounting.domain.view.TrialBalanceLineView;
import com.paytech.finance.accounting.domainservice.repository.TrialBalanceLineRepository;

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
    public List<TrialBalanceLineView> findViews(long runId) {
        return mapper.selectViews(runId);
    }
}
