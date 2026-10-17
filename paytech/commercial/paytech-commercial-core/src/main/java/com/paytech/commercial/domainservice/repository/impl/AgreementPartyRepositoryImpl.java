package com.paytech.commercial.domainservice.repository.impl;

import java.util.List;

import com.paytech.commercial.core.dal.dataobject.CheckoutRecords.AgreementParty;
import com.paytech.commercial.core.dal.mapper.AgreementPartyMapper;
import com.paytech.commercial.domainservice.repository.AgreementPartyRepository;

import lombok.AllArgsConstructor;

/** 只通过 AgreementPartyMapper 读取 t_agreement_party。 */
@AllArgsConstructor
public class AgreementPartyRepositoryImpl implements AgreementPartyRepository {
    private final AgreementPartyMapper mapper;

    /** 读取本 Client Checkout 范围内的 agreement_party 记录，保留历史和未生效事实供业务判断。 */
    @Override
    public List<AgreementParty> findForCheckout(long clientId) {
        return List.copyOf(mapper.findForCheckout(clientId));
    }
}
