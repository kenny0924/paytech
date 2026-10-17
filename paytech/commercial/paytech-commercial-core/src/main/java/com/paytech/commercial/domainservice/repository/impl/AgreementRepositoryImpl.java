package com.paytech.commercial.domainservice.repository.impl;

import java.util.List;

import com.paytech.commercial.domain.CommercialCheckoutScope;
import com.paytech.commercial.core.dal.dataobject.CheckoutRecords.Agreement;
import com.paytech.commercial.core.dal.mapper.AgreementMapper;
import com.paytech.commercial.domainservice.repository.AgreementRepository;

import lombok.AllArgsConstructor;

/** 只通过 AgreementMapper 读取 t_agreement。 */
@AllArgsConstructor
public class AgreementRepositoryImpl implements AgreementRepository {
    private final AgreementMapper mapper;

    /** 读取本 Client Checkout 范围（Client 及 client-master 返回的 OE 与 Local Partner）内的 agreement 记录，保留历史和未生效事实供业务判断。 */
    @Override
    public List<Agreement> findForCheckout(CommercialCheckoutScope scope) {
        return List.copyOf(mapper.findForCheckout(scope.clientId(), scope.entityNos(), scope.localPartnerIds()));
    }
}
