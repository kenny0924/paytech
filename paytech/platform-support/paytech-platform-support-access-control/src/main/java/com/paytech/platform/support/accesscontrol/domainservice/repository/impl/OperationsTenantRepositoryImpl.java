package com.paytech.platform.support.accesscontrol.domainservice.repository.impl;

import com.paytech.platform.support.accesscontrol.core.dal.mapper.OperationsIdentityMapper;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsTenantRepository;
import org.springframework.stereotype.Repository;
import lombok.AllArgsConstructor;

/** 条件写成功后同步原对象；事务由完整用例控制。 */
@Repository
@AllArgsConstructor
public class OperationsTenantRepositoryImpl implements OperationsTenantRepository {
    private final OperationsIdentityMapper mapper;
    /** 读取有效固定 INTERNAL Tenant。 */
    @Override
    public Long internalTenant() {
        return mapper.internalTenant();
    }

}
