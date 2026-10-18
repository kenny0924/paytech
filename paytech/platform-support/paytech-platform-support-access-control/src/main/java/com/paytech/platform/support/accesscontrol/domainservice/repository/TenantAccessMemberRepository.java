package com.paytech.platform.support.accesscontrol.domainservice.repository;

import java.util.List;

import com.paytech.platform.support.accesscontrol.domain.tenantaccess.MemberFilter;
import com.paytech.platform.support.accesscontrol.domain.tenantaccess.TenantMemberView;

/** 以租户成员表为入口的账号关联查询，只读取本模块事实。 */
public interface TenantAccessMemberRepository {

    /** 按租户及成员精确读取安全字段。 */
    TenantMemberView member(long tenantId, long memberId);

    /** 分页读取指定租户的成员。 */
    List<TenantMemberView> members(long tenantId, MemberFilter filter, long offset, int size);

    /** 按相同筛选统计成员数。 */
    long memberCount(long tenantId, MemberFilter filter);

    /** 检测本租户中缺失或非法账号关联。 */
    long invalidMembers(long tenantId);
}
