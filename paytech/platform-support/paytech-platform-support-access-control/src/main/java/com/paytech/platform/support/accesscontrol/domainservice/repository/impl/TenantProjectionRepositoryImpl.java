package com.paytech.platform.support.accesscontrol.domainservice.repository.impl;

import org.springframework.stereotype.Repository;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TenantProjectionAccount;
import com.paytech.platform.support.accesscontrol.core.dal.mapper.TenantProjectionMapper;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TenantProjectionRepository;

/** MyBatis 实现不自行开启事务或调用其他系统。 */
@Repository
@lombok.RequiredArgsConstructor
public class TenantProjectionRepositoryImpl implements TenantProjectionRepository {
    private final TenantProjectionMapper mapper;

    /** 锁定申请人账号，始终先于 Tenant 和 Member 行锁。 */
    @Override
    public TenantProjectionAccount lockAccount(long id) {
        return mapper.lockAccount(id);
    }
}
