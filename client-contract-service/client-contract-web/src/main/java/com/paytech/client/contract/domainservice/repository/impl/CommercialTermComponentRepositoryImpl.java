package com.paytech.client.contract.domainservice.repository.impl;

import java.util.List;

import com.paytech.client.contract.core.dal.dataobject.CheckoutRecords.CommercialTermComponent;
import com.paytech.client.contract.core.dal.mapper.CommercialTermComponentMapper;
import com.paytech.client.contract.domainservice.repository.CommercialTermComponentRepository;

import lombok.AllArgsConstructor;

/** 只通过 CommercialTermComponentMapper 读取 t_commercial_term_component。 */
@AllArgsConstructor
public class CommercialTermComponentRepositoryImpl implements CommercialTermComponentRepository {
    private final CommercialTermComponentMapper mapper;

    /** 读取本 Client Checkout 范围内的 commercial_term_component 记录，保留历史和未生效事实供业务判断。 */
    @Override
    public List<CommercialTermComponent> findForCheckout(long clientId) {
        return List.copyOf(mapper.findForCheckout(clientId));
    }
}
