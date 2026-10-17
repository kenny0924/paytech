package com.paytech.commercial.domainservice.repository.impl;

import java.util.List;

import com.paytech.commercial.domain.CommercialCheckoutScope;
import com.paytech.commercial.core.dal.dataobject.CheckoutRecords.AgreementDocumentRef;
import com.paytech.commercial.core.dal.mapper.AgreementDocumentRefMapper;
import com.paytech.commercial.domainservice.repository.AgreementDocumentRefRepository;

import lombok.AllArgsConstructor;

/** 只通过 AgreementDocumentRefMapper 读取 t_agreement_document_ref。 */
@AllArgsConstructor
public class AgreementDocumentRefRepositoryImpl implements AgreementDocumentRefRepository {
    private final AgreementDocumentRefMapper mapper;

    /** 读取本 Client Checkout 范围（Client 及 client-master 返回的 OE 与 Local Partner）内的 agreement_document_ref 记录，保留历史和未生效事实供业务判断。 */
    @Override
    public List<AgreementDocumentRef> findForCheckout(CommercialCheckoutScope scope) {
        return List.copyOf(mapper.findForCheckout(scope.clientId(), scope.entityNos(), scope.localPartnerIds()));
    }
}
