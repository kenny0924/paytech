package com.paytech.client.contract.domainservice.repository.impl;

import java.util.List;

import com.paytech.client.contract.core.dal.dataobject.CheckoutRecords.ClientBusinessClassification;
import com.paytech.client.contract.core.dal.mapper.ClientBusinessClassificationMapper;
import com.paytech.client.contract.domainservice.repository.ClientBusinessClassificationRepository;

import lombok.AllArgsConstructor;

/** 只通过 ClientBusinessClassificationMapper 读取 t_client_business_classification。 */
@AllArgsConstructor
public class ClientBusinessClassificationRepositoryImpl implements ClientBusinessClassificationRepository {
    private final ClientBusinessClassificationMapper mapper;

    /** 读取本 Client Checkout 范围内的 client_business_classification 记录，保留历史和未生效事实供业务判断。 */
    @Override
    public List<ClientBusinessClassification> findForCheckout(long clientId) {
        return List.copyOf(mapper.findForCheckout(clientId));
    }
}
