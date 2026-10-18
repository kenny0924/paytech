package com.paytech.platform.support.accesscontrol.domainservice.repository.impl;

import java.time.Instant;

import org.springframework.stereotype.Repository;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TenantProjectionAccount;
import com.paytech.platform.support.accesscontrol.core.dal.mapper.TenantProjectionMapper;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TenantProjectionRepository;

/** MyBatis 实现不自行开启事务或调用其他系统。 */
@Repository
@lombok.RequiredArgsConstructor
public class TenantProjectionRepositoryImpl implements TenantProjectionRepository {
    private final TenantProjectionMapper mapper;

    /** 只读权限查询不获取写锁。 */
    @Override public java.util.List<com.paytech.platform.support.accesscontrol.core.dal.dataobject.OnboardingTenantCandidate> fileReadCandidates(long accountId,long tenantId,Instant now) { return mapper.fileReadCandidates(accountId,tenantId,now); }
    /** 读取客户账号当前事实。 */
    @Override public TenantProjectionAccount fileAccount(long id) { return mapper.fileAccount(id); }
    /** 原 Tenant 的读权限仍须完整校验当前角色与授权时效。 */
    @Override public java.util.List<com.paytech.platform.support.accesscontrol.core.dal.dataobject.OnboardingTenantCandidate> lockFileReadCandidates(long accountId,long tenantId,Instant now) {
        return mapper.lockFileReadCandidates(accountId,tenantId,now);
    }
    /** 所有入驻候选在同一语句中核对完整租户、成员、角色和时效。 */
    @Override
    public java.util.List<com.paytech.platform.support.accesscontrol.core.dal.dataobject.OnboardingTenantCandidate> lockUploadCandidates(long accountId, Instant now) {
        return mapper.lockUploadCandidates(accountId, now);
    }

    /** 锁定申请人账号，始终先于 Tenant 和 Member 行锁。 */
    @Override
    public TenantProjectionAccount lockAccount(long id) {
        return mapper.lockAccount(id);
    }
}
