package com.paytech.commercial.domainservice.repository.impl;

import java.util.List;

import com.paytech.commercial.domain.CommercialCheckoutScope;
import com.paytech.commercial.core.dal.dataobject.CheckoutRecords.CommercialTermVersion;
import com.paytech.commercial.core.dal.mapper.CommercialTermVersionMapper;
import com.paytech.commercial.domainservice.repository.CommercialTermVersionRepository;

import lombok.AllArgsConstructor;

/** 只通过 CommercialTermVersionMapper 读取 t_commercial_term_version。 */
@AllArgsConstructor
public class CommercialTermVersionRepositoryImpl implements CommercialTermVersionRepository {
    private final CommercialTermVersionMapper mapper;

    /** 读取本 Client Checkout 范围（Client 及 client-master 返回的 OE 与 Local Partner）内的 commercial_term_version 记录，保留历史和未生效事实供业务判断。 */
    @Override
    public List<CommercialTermVersion> findForCheckout(CommercialCheckoutScope scope) {
        return List.copyOf(mapper.findForCheckout(scope.clientId(), scope.entityNos(), scope.localPartnerIds()));
    }
}
