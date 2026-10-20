package com.paytech.ledger.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.ledger.core.dal.dataobject.ChartAccountChange;
import com.paytech.ledger.domain.view.ChartAccountChangeView;
import com.paytech.ledger.core.dal.mapper.ChartAccountChangeMapper;
import com.paytech.ledger.domainservice.repository.ChartAccountChangeRepository;

import lombok.AllArgsConstructor;

/** ChartAccountChangeRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class ChartAccountChangeRepositoryImpl implements ChartAccountChangeRepository {
    private final ChartAccountChangeMapper mapper;

    /** 追加一条变更记录。 */
    @Override
    public void insert(ChartAccountChange change) {
        mapper.insert(change);
    }

    /** 分页读取科目表的变更记录。 */
    @Override
    public List<ChartAccountChangeView> findPage(long chartId, Long chartAccountId, PagingRequestBody paging) {
        return mapper.selectPage(chartId, chartAccountId, paging.size(), paging.offset());
    }

    /** 统计符合条件的变更记录数。 */
    @Override
    public long countPage(long chartId, Long chartAccountId) {
        return mapper.countPage(chartId, chartAccountId);
    }
}
