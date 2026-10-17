package com.paytech.platform.support.accesscontrol.domainservice.repository;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TeamTenant;

/** CLIENT 租户状态（t_access_tenant）的团队管理只读合同。 */
public interface TeamTenantRepository {
    /** 读取 CLIENT 租户；不存在为空。 */
    TeamTenant tenant(long tenantId);
}
