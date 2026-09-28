package com.paytech.client.contract.domainservice.repository.impl;

import java.util.List;

import com.paytech.client.contract.core.dal.dataobject.CheckoutRecords.ClientEntityAffiliation;
import com.paytech.client.contract.core.dal.mapper.ClientEntityAffiliationMapper;
import com.paytech.client.contract.domainservice.repository.ClientEntityAffiliationRepository;

import lombok.AllArgsConstructor;

/** 只通过 ClientEntityAffiliationMapper 读取 t_client_entity_affiliation。 */
@AllArgsConstructor
public class ClientEntityAffiliationRepositoryImpl implements ClientEntityAffiliationRepository {
    private final ClientEntityAffiliationMapper mapper;

    /** 读取本 Client Checkout 范围内的 client_entity_affiliation 记录，保留历史和未生效事实供业务判断。 */
    @Override
    public List<ClientEntityAffiliation> findByClientId(long clientId) {
        return List.copyOf(mapper.findByClientId(clientId));
    }
}
