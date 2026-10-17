package com.paytech.platform.support.accesscontrol.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TeamMemberGrant;

/** 成员角色授予（t_tenant_member_role）的团队管理持久化合同；条件更新成功后同步传入对象。 */
public interface TeamMemberRoleRepository {
    /** 行锁读取成员当前全部 ACTIVE 授予。 */
    List<TeamMemberGrant> lockActive(long tenantId, long memberId);

    /** 撤销一项授予。 */
    void revoke(TeamMemberGrant grant, Instant now);

    /** 写入一项 ACTIVE 授予。 */
    void insert(TeamMemberGrant grant, Instant now);
}
