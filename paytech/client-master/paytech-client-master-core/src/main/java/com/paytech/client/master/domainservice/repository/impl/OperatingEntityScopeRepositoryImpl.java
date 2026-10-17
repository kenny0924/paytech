package com.paytech.client.master.domainservice.repository.impl;

import java.util.List;

import com.paytech.client.master.core.dal.dataobject.CheckoutRecords.OperatingEntityScope;
import com.paytech.client.master.core.dal.mapper.OperatingEntityScopeMapper;
import com.paytech.client.master.domainservice.repository.OperatingEntityScopeRepository;

import lombok.AllArgsConstructor;

/** 只通过 OperatingEntityScopeMapper 读取 t_operating_entity_scope。 */
@AllArgsConstructor
public class OperatingEntityScopeRepositoryImpl implements OperatingEntityScopeRepository {
    private final OperatingEntityScopeMapper mapper;

    /** 读取本 Client Checkout 范围内的 operating_entity_scope 记录，保留历史和未生效事实供业务判断。 */
    @Override
    public List<OperatingEntityScope> findForCheckout(long clientId) {
        return List.copyOf(mapper.findForCheckout(clientId));
    }
}
