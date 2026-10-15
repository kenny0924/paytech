package com.paytech.finance.accounting.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.finance.accounting.core.dal.dataobject.TrialBalanceException;
import com.paytech.finance.accounting.core.dal.mapper.TrialBalanceExceptionMapper;
import com.paytech.finance.accounting.domain.view.TrialBalanceFindingView;
import com.paytech.finance.accounting.domainservice.repository.TrialBalanceExceptionRepository;

import lombok.AllArgsConstructor;

/** TrialBalanceExceptionRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class TrialBalanceExceptionRepositoryImpl implements TrialBalanceExceptionRepository {
    private final TrialBalanceExceptionMapper mapper;

    /** 批量插入异常；空列表不访问数据库。 */
    @Override
    public void insertBatch(List<TrialBalanceException> exceptions) {
        if (!exceptions.isEmpty()) {
            mapper.insertBatch(exceptions);
        }
    }

    /** 读取一次试算的全部异常。 */
    @Override
    public List<TrialBalanceFindingView> findViews(long runId) {
        return mapper.selectViews(runId);
    }
}
