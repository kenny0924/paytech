package com.paytech.commercial.domainservice.repository.impl;

import static com.paytech.commercial.domainservice.repository.impl.PersistenceTimes.micros;

import java.util.Collection;
import java.util.List;

import com.paytech.commercial.core.dal.dataobject.CheckoutRecords;
import com.paytech.commercial.core.dal.dataobject.CommercialTermComponent;
import com.paytech.commercial.core.dal.mapper.CommercialTermComponentMapper;
import com.paytech.commercial.domain.CommercialCheckoutScope;
import com.paytech.commercial.domainservice.repository.CommercialTermComponentRepository;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** 只通过 CommercialTermComponentMapper 读取 t_commercial_term_component。 */
@AllArgsConstructor
public class CommercialTermComponentRepositoryImpl implements CommercialTermComponentRepository {
    private final CommercialTermComponentMapper mapper;

    /** 读取本 Client Checkout 范围（Client 及 client-master 返回的 OE 与 Local Partner）内的 commercial_term_component 记录，保留历史和未生效事实供业务判断。 */
    @Override
    public List<CheckoutRecords.CommercialTermComponent> findForCheckout(CommercialCheckoutScope scope) {
        return List.copyOf(mapper.findForCheckout(scope.clientId(), scope.entityNos(), scope.localPartnerIds()));
    }

    /** 空列表不访问数据库；截断时间精度后批量插入并核对影响行数。 */
    @Override
    public void insertAll(List<CommercialTermComponent> rows) {
        if (rows.isEmpty()) {
            return;
        }
        for (CommercialTermComponent row : rows) {
            row.setCreateTime(micros(row.getCreateTime()));
            row.setUpdateTime(micros(row.getUpdateTime()));
        }
        int inserted = mapper.insertAll(rows);
        if (inserted != rows.size()) {
            throw new ErrorException("商业条款组件插入行数不符: 期望 " + rows.size() + ", 实际 " + inserted);
        }
    }

    /** 按主键读取。 */
    @Override
    public CommercialTermComponent findById(long termComponentId) {
        return mapper.selectById(termComponentId);
    }

    /** 按唯一键（价格、组件代码）读取。 */
    @Override
    public CommercialTermComponent findByUniqueKey(long termPriceId, String componentCode) {
        return mapper.selectByUniqueKey(termPriceId, componentCode);
    }

    /** 空集合不访问数据库。 */
    @Override
    public List<CommercialTermComponent> findByPriceIds(Collection<Long> termPriceIds) {
        if (termPriceIds.isEmpty()) {
            return List.of();
        }
        return List.copyOf(mapper.selectByPriceIds(termPriceIds));
    }

    /** 空集合不访问数据库。 */
    @Override
    public int deleteByPriceIds(Collection<Long> termPriceIds) {
        if (termPriceIds.isEmpty()) {
            return 0;
        }
        return mapper.deleteByPriceIds(termPriceIds);
    }
}
