package com.paytech.client.contract.domainservice.repository.impl;

import java.util.List;

import com.paytech.client.contract.core.dal.dataobject.CheckoutRecords.OperatingEntityApplication;
import com.paytech.client.contract.core.dal.mapper.OperatingEntityApplicationMapper;
import com.paytech.client.contract.domainservice.repository.OperatingEntityApplicationRepository;

import lombok.AllArgsConstructor;

/** 只通过 OperatingEntityApplicationMapper 读取 t_operating_entity_application。 */
@AllArgsConstructor
public class OperatingEntityApplicationRepositoryImpl implements OperatingEntityApplicationRepository {
    private final OperatingEntityApplicationMapper mapper;

    /** 读取本 Client Checkout 范围内的 operating_entity_application 记录，保留历史和未生效事实供业务判断。 */
    @Override
    public List<OperatingEntityApplication> findForCheckout(long clientId) {
        return List.copyOf(mapper.findForCheckout(clientId));
    }
}
