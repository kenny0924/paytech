package com.paytech.platform.support.accesscontrol.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsRole;

/** Operations 角色事实访问。 */
public interface OperationsRoleRepository {
    /** 读取当前可授予的角色目录。 */
    List<OperationsRole> roles();

    /** 保存已验证的新角色。 */
    void insert(OperationsRole role, long tenantId, Instant now);

    /** 按原聚合版本更新名称。 */
    void update(OperationsRole role, long tenantId, String name, Instant now);

    /** 按原聚合版本逻辑删除自定义角色。 */
    void delete(OperationsRole role, long tenantId, Instant now);
}
