package com.paytech.platform.support.accesscontrol.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TenantProjectionMember;

/** 来源投影中该表的本地持久化边界。 */
public interface TenantProjectionMemberRepository {

    /** 读取初始 OWNER，不过滤独立成员状态。 */
    List<TenantProjectionMember> owners(long tenantId);

    /** 保存原申请账号的 OWNER 成员。 */
    void insert(TenantProjectionMember member, long tenantId, Instant now);
}
