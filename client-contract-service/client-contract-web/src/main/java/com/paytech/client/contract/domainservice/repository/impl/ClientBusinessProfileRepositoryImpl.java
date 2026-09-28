package com.paytech.client.contract.domainservice.repository.impl;

import java.util.List;

import com.paytech.client.contract.core.dal.dataobject.CheckoutRecords.ClientBusinessProfile;
import com.paytech.client.contract.core.dal.mapper.ClientBusinessProfileMapper;
import com.paytech.client.contract.domainservice.repository.ClientBusinessProfileRepository;

import lombok.AllArgsConstructor;

/** 只通过 ClientBusinessProfileMapper 读取 t_client_business_profile。 */
@AllArgsConstructor
public class ClientBusinessProfileRepositoryImpl implements ClientBusinessProfileRepository {
    private final ClientBusinessProfileMapper mapper;

    /** 读取本 Client Checkout 范围内的 client_business_profile 记录，保留历史和未生效事实供业务判断。 */
    @Override
    public List<ClientBusinessProfile> findByClientId(long clientId) {
        return List.copyOf(mapper.findByClientId(clientId));
    }
}
