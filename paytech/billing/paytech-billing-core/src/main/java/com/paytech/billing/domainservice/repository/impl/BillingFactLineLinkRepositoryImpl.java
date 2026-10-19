package com.paytech.billing.domainservice.repository.impl;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.billing.core.dal.dataobject.BillingFactLineLink;
import com.paytech.billing.core.dal.mapper.BillingFactLineLinkMapper;
import com.paytech.billing.domainservice.repository.BillingFactLineLinkRepository;

import lombok.AllArgsConstructor;

/** BillingFactLineLinkRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class BillingFactLineLinkRepositoryImpl implements BillingFactLineLinkRepository {
    private final BillingFactLineLinkMapper mapper;

    /** 以给定明细行为原行的链接；空集合不发出 SQL。 */
    @Override
    public List<BillingFactLineLink> findBySourceLineIds(Collection<Long> lineIds) {
        if (lineIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectBySourceLineIds(lineIds);
    }

    /** 以给定明细行为新行的链接；空集合不发出 SQL。 */
    @Override
    public List<BillingFactLineLink> findByTargetLineIds(Collection<Long> lineIds) {
        if (lineIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectByTargetLineIds(lineIds);
    }
}
