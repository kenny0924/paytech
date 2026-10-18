package com.paytech.client.master.domainservice.repository.impl;

import java.util.List;

import com.paytech.client.master.core.dal.dataobject.CheckoutRecords.LocalPartner;
import com.paytech.client.master.core.dal.mapper.LocalPartnerMapper;
import com.paytech.client.master.domainservice.repository.LocalPartnerRepository;

import lombok.AllArgsConstructor;

/** 只通过 LocalPartnerMapper 读取 t_local_partner。 */
@AllArgsConstructor
public class LocalPartnerRepositoryImpl implements LocalPartnerRepository {
    private final LocalPartnerMapper mapper;

    /** 读取本 Client Checkout 范围内的 local_partner 记录，保留历史和未生效事实供业务判断。 */
    @Override
    public List<LocalPartner> findForCheckout(long clientId) {
        return List.copyOf(mapper.findForCheckout(clientId));
    }
}
