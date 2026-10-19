package com.paytech.billing.domainservice.repository.impl;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.billing.api.enums.FactRefType;
import com.paytech.billing.core.dal.dataobject.BillingFactRef;
import com.paytech.billing.core.dal.mapper.BillingFactRefMapper;
import com.paytech.billing.domainservice.repository.BillingFactRefRepository;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** BillingFactRefRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class BillingFactRefRepositoryImpl implements BillingFactRefRepository {
    private final BillingFactRefMapper mapper;

    /** 一次插入全部引用；空列表不发出 SQL。 */
    @Override
    public void insertAll(List<BillingFactRef> refs) {
        if (refs.isEmpty()) {
            return;
        }
        if (mapper.batchInsert(refs) != refs.size()) {
            throw new ErrorException("计费事实引用插入行数不符: 期望 " + refs.size());
        }
    }

    /** 一条事实的全部引用。 */
    @Override
    public List<BillingFactRef> findByFactId(long factId) {
        return mapper.selectByFactId(factId);
    }

    /** 一组事实的全部引用；空集合不发出 SQL。 */
    @Override
    public List<BillingFactRef> findByFactIds(Collection<Long> factIds) {
        if (factIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectByFactIds(factIds);
    }

    /** 引用给定外部对象的事实 ID。 */
    @Override
    public List<Long> findFactIdsByRef(FactRefType refType, String refId, long fromFactId, int limit) {
        return mapper.selectFactIdsByRef(refType, refId, fromFactId, limit);
    }
}
