package com.paytech.platform.support.accesscontrol.domainservice.repository;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TeamRole;

/** 工作区租户系统角色（t_role）的团队管理只读合同。 */
public interface TeamRoleRepository {
    /** 按代码读取本工作区系统角色；不存在为空。 */
    TeamRole systemRole(long tenantId, String roleCode);

    /** 按主键读取本工作区系统角色；不存在为空。 */
    TeamRole role(long tenantId, long roleId);
}
