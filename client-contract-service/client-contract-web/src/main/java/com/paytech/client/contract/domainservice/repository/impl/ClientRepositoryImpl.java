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

    /** 条件激活。 */
    @Override
    public void activate(long clientId, java.time.Instant now) {
        if (mapper.activate(clientId, now) != 1) {
            throw new com.paytech.foundation.common.error.ConcurrentRequestException("Client 状态已变化");
        }
    }
}
