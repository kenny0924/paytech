package com.paytech.client.contract.domainservice.repository.impl;

import java.util.List;

import com.paytech.client.contract.core.dal.dataobject.CheckoutRecords.Agreement;
import com.paytech.client.contract.core.dal.mapper.AgreementMapper;
import com.paytech.client.contract.domainservice.repository.AgreementRepository;

import lombok.AllArgsConstructor;

/** 只通过 AgreementMapper 读取 t_agreement。 */
@AllArgsConstructor
public class AgreementRepositoryImpl implements AgreementRepository {
    private final AgreementMapper mapper;

    /** 读取本 Client Checkout 范围内的 agreement 记录，保留历史和未生效事实供业务判断。 */
    @Override
    public List<Agreement> findForCheckout(long clientId) {
        return List.copyOf(mapper.findForCheckout(clientId));
    }
}
