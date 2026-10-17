package com.paytech.platform.support.accesscontrol.domainservice.repository;

import java.time.Instant;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TenantProjectionAccount;

/** 封装工作区来源投影的本地持久化能力。 */
public interface TenantProjectionRepository {
    /** 无副作用地读取准确 Tenant 当前权限。 */
    java.util.List<com.paytech.platform.support.accesscontrol.core.dal.dataobject.OnboardingTenantCandidate> fileReadCandidates(long accountId,long tenantId,Instant now);
    /** 私网核验需要当前真实客户账号。 */
    TenantProjectionAccount fileAccount(long id);

    /** 只读命令回执使用原 Tenant 当前权限，不重新选择最新入驻身份。 */
    java.util.List<com.paytech.platform.support.accesscontrol.core.dal.dataobject.OnboardingTenantCandidate> lockFileReadCandidates(long accountId,long tenantId,Instant now);

    /** 锁定全部有效入驻候选，不按预期 Client 预过滤以掩盖残留工作区。 */
    java.util.List<com.paytech.platform.support.accesscontrol.core.dal.dataobject.OnboardingTenantCandidate> lockUploadCandidates(
        long accountId, Instant now);

    /** 锁定申请人账号，始终先于 Tenant 和 Member 行锁。 */
    TenantProjectionAccount lockAccount(long id);
}
