package com.paytech.commercial.domainservice.repository.impl;

import static com.paytech.commercial.domainservice.repository.impl.PersistenceTimes.micros;

import java.util.Collection;
import java.util.List;

import com.paytech.commercial.core.dal.dataobject.CheckoutRecords;
import com.paytech.commercial.core.dal.dataobject.CommercialTermComponentBand;
import com.paytech.commercial.core.dal.mapper.CommercialTermComponentBandMapper;
import com.paytech.commercial.domain.CommercialCheckoutScope;
import com.paytech.commercial.domainservice.repository.CommercialTermComponentBandRepository;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** 只通过 CommercialTermComponentBandMapper 读取 t_commercial_term_component_band。 */
@AllArgsConstructor
public class CommercialTermComponentBandRepositoryImpl implements CommercialTermComponentBandRepository {
    private final CommercialTermComponentBandMapper mapper;

    /** 读取本 Client Checkout 范围（Client 及 client-master 返回的 OE 与 Local Partner）内的 commercial_term_component_band 记录，保留历史和未生效事实供业务判断。 */
    @Override
    public List<CheckoutRecords.CommercialTermComponentBand> findForCheckout(CommercialCheckoutScope scope) {
        return List.copyOf(mapper.findForCheckout(scope.clientId(), scope.entityNos(), scope.localPartnerIds()));
    }

    /** 空列表不访问数据库；截断时间精度后批量插入并核对影响行数。 */
    @Override
    public void insertAll(List<CommercialTermComponentBand> rows) {
        if (rows.isEmpty()) {
            return;
        }
        for (CommercialTermComponentBand row : rows) {
            row.setCreateTime(micros(row.getCreateTime()));
            row.setUpdateTime(micros(row.getUpdateTime()));
        }
        int inserted = mapper.insertAll(rows);
        if (inserted != rows.size()) {
            throw new ErrorException("商业条款档位插入行数不符: 期望 " + rows.size() + ", 实际 " + inserted);
        }
    }

    /** 按主键读取。 */
    @Override
    public CommercialTermComponentBand findById(long termBandId) {
        return mapper.selectById(termBandId);
    }

    /** 空集合不访问数据库。 */
    @Override
    public List<CommercialTermComponentBand> findByComponentIds(Collection<Long> termComponentIds) {
        if (termComponentIds.isEmpty()) {
            return List.of();
        }
        return List.copyOf(mapper.selectByComponentIds(termComponentIds));
    }

    /** 空集合不访问数据库。 */
    @Override
    public int deleteByComponentIds(Collection<Long> termComponentIds) {
        if (termComponentIds.isEmpty()) {
            return 0;
        }
        return mapper.deleteByComponentIds(termComponentIds);
    }
}
