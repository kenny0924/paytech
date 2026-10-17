package com.paytech.commercial.domainservice.repository.impl;

import java.util.List;

import com.paytech.commercial.domain.CommercialCheckoutScope;
import com.paytech.commercial.core.dal.dataobject.CheckoutRecords.AgreementParty;
import com.paytech.commercial.core.dal.mapper.AgreementPartyMapper;
import com.paytech.commercial.domainservice.repository.AgreementPartyRepository;

import lombok.AllArgsConstructor;

/** 只通过 AgreementPartyMapper 读取 t_agreement_party。 */
@AllArgsConstructor
public class AgreementPartyRepositoryImpl implements AgreementPartyRepository {
    private final AgreementPartyMapper mapper;

    /** 读取本 Client Checkout 范围（Client 及 client-master 返回的 OE 与 Local Partner）内的 agreement_party 记录，保留历史和未生效事实供业务判断。 */
    @Override
    public List<AgreementParty> findForCheckout(CommercialCheckoutScope scope) {
        return List.copyOf(mapper.findForCheckout(scope.clientId(), scope.entityNos(), scope.localPartnerIds()));
    }

    /** 被任一合同参与方引用的 Party ID；空集合不访问数据库。 */
    @Override
    public java.util.Set<Long> findReferencedPartyIds(java.util.Collection<Long> partyIds) {
        if (partyIds.isEmpty()) {
            return java.util.Set.of();
        }
        return new java.util.TreeSet<>(mapper.findReferencedPartyIds(partyIds));
    }

    /** 被任一合同参与方引用的资料 ID；空集合不访问数据库。 */
    @Override
    public java.util.Set<Long> findReferencedPartyProfileIds(java.util.Collection<Long> partyProfileIds) {
        if (partyProfileIds.isEmpty()) {
            return java.util.Set.of();
        }
        return new java.util.TreeSet<>(mapper.findReferencedPartyProfileIds(partyProfileIds));
    }
}
