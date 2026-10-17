package com.paytech.platform.support.accesscontrol.domainservice.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.Set;

/** CLIENT 工作区成员当前权限（成员角色授予、角色、角色权限、权限）的只读持久化合同。 */
public interface ClientPermissionRepository {
    /** 返回给定权限码中当前已授予该成员的部分。 */
    Set<String> granted(long memberId, long tenantId, Collection<String> codes, Instant now);
}
