package com.paytech.client.contract.domainservice.repository.impl;

import com.paytech.client.contract.core.dal.dataobject.CheckoutRecords.Client;
import com.paytech.client.contract.core.dal.mapper.ClientMapper;
import com.paytech.client.contract.domainservice.repository.ClientRepository;

import lombok.AllArgsConstructor;

/** 只通过 ClientMapper 读取 t_client。 */
@AllArgsConstructor
public class ClientRepositoryImpl implements ClientRepository {
    private final ClientMapper mapper;

    /** 按 Client 身份读取客户；不存在时返回 null。 */
    @Override
    public Client findById(long clientId) {
        return mapper.findById(clientId);
    }
}
