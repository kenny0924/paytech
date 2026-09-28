package com.paytech.client.contract.domainservice.repository.impl;

import java.util.List;

import com.paytech.client.contract.core.dal.dataobject.CheckoutRecords.BusinessChangeRecord;
import com.paytech.client.contract.core.dal.mapper.BusinessChangeRecordMapper;
import com.paytech.client.contract.domainservice.repository.BusinessChangeRecordRepository;

import lombok.AllArgsConstructor;

/** 只通过 BusinessChangeRecordMapper 读取 t_business_change_record。 */
@AllArgsConstructor
public class BusinessChangeRecordRepositoryImpl implements BusinessChangeRecordRepository {
    private final BusinessChangeRecordMapper mapper;

    /** 读取影响本 Client Checkout 候选的业务根修订。 */
    @Override
    public List<BusinessChangeRecord> findCheckoutRoots(long clientId) {
        return List.copyOf(mapper.findCheckoutRoots(clientId));
    }
}
