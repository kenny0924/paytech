package com.paytech.client.contract.domainservice.repository.impl;

import java.util.List;

import com.paytech.client.contract.core.dal.dataobject.CheckoutRecords.OperatingEntity;
import com.paytech.client.contract.core.dal.mapper.OperatingEntityMapper;
import com.paytech.client.contract.domainservice.repository.OperatingEntityRepository;

import lombok.AllArgsConstructor;

/** 只通过 OperatingEntityMapper 读取 t_operating_entity。 */
@AllArgsConstructor
public class OperatingEntityRepositoryImpl implements OperatingEntityRepository {
    private final OperatingEntityMapper mapper;

    /** 读取本 Client Checkout 范围内的 operating_entity 记录，保留历史和未生效事实供业务判断。 */
    @Override
    public List<OperatingEntity> findForCheckout(long clientId) {
        return List.copyOf(mapper.findForCheckout(clientId));
    }
}
