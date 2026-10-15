package com.paytech.finance.accounting.domainservice.repository.impl;

import org.springframework.stereotype.Repository;

import com.paytech.finance.accounting.core.dal.dataobject.ChartAccount;
import com.paytech.finance.accounting.core.dal.mapper.ChartAccountMapper;
import com.paytech.finance.accounting.domainservice.repository.ChartAccountRepository;

import lombok.AllArgsConstructor;

/** ChartAccountRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class ChartAccountRepositoryImpl implements ChartAccountRepository {
    private final ChartAccountMapper mapper;

    /** 按主键读取科目，不存在返回空。 */
    @Override
    public ChartAccount findById(long id) {
        return mapper.selectById(id);
    }

    /** 按科目表和科目代码读取，不存在返回空。 */
    @Override
    public ChartAccount findByChartAndCode(long chartId, String accountCode) {
        return mapper.selectByChartAndCode(chartId, accountCode);
    }
}
