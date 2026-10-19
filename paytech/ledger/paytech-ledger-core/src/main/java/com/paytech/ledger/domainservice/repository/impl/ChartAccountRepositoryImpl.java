package com.paytech.ledger.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.ledger.core.dal.dataobject.ChartAccount;
import com.paytech.ledger.core.dal.mapper.ChartAccountMapper;
import com.paytech.ledger.domainservice.repository.ChartAccountRepository;

import lombok.AllArgsConstructor;

/** ChartAccountRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class ChartAccountRepositoryImpl implements ChartAccountRepository {
    private final ChartAccountMapper mapper;

    /** 按主键升序读取一组科目；空集合返回空列表。 */
    @Override
    public List<ChartAccount> findByIds(List<Long> ids) {
        return ids.isEmpty() ? List.of() : mapper.selectByIds(ids);
    }

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

    /** 读取科目表下的科目；postingAllowed 为空时不过滤，按科目代码升序。 */
    @Override
    public List<ChartAccount> findByChart(long chartId, Boolean postingAllowed) {
        return mapper.selectByChart(chartId, postingAllowed);
    }
}
