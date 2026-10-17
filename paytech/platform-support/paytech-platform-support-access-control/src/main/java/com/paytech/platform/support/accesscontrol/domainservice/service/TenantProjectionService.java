package com.paytech.platform.support.accesscontrol.domainservice.service;

import com.paytech.platform.support.accesscontrol.domain.tenantaccess.ProjectTenantStatusContext;
import com.paytech.platform.support.accesscontrol.domain.tenantaccess.ProvisionTenantContext;

/** 只保存已确认的来源事实，事务与重放判断归 Owner Processor。 */
public interface TenantProjectionService {
    /** 原子阶段中保存 Tenant、OWNER、入驻角色与授予。 */
    void provision(ProvisionTenantContext context);

    /** 同一阶段保存来源状态及必要的首次 OWNER 角色切换。 */
    void projectStatus(ProjectTenantStatusContext context);
}
