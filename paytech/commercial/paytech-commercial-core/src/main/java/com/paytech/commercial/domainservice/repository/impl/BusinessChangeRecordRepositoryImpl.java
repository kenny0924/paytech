package com.paytech.commercial.domainservice.repository.impl;

import java.util.List;

import com.paytech.commercial.core.dal.dataobject.CheckoutRecords.BusinessChangeRecord;
import com.paytech.commercial.core.dal.mapper.BusinessChangeRecordMapper;
import com.paytech.commercial.domain.CommercialCheckoutScope;
import com.paytech.commercial.domainservice.repository.BusinessChangeRecordRepository;

import lombok.AllArgsConstructor;

/** 只通过 BusinessChangeRecordMapper 读取 t_business_change_record。 */
@AllArgsConstructor
public class BusinessChangeRecordRepositoryImpl implements BusinessChangeRecordRepository {
    private final BusinessChangeRecordMapper mapper;

    /** 读取本次 Checkout 范围内合同的 AGREEMENT 根修订。 */
    @Override
    public List<BusinessChangeRecord> findCheckoutRoots(CommercialCheckoutScope scope) {
        return List.copyOf(mapper.findCheckoutRoots(scope.clientId(), scope.entityNos(), scope.localPartnerIds()));
    }
}
