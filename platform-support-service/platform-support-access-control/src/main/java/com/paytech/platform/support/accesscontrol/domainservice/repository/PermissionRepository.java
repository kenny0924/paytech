package com.paytech.platform.support.accesscontrol.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.Permission;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConfigurationCatalog.PermissionFilter;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsPermissionRiskLevel;
import com.paytech.platform.support.accesscontrol.domain.enums.PermissionStatus;

/** t_permission 的查询和原子保存，不接受 Context 或跨表写入。 */
public interface PermissionRepository {
    /** 按同一过滤条件读取有界分页。 */
    List<Permission> permissions(PermissionFilter filter, int offset, int size);
    /** 与列表使用相同过滤条件的总数。 */
    long count(PermissionFilter filter);
    /** 读取稳定编码对应的权限，不存在返回 null。 */
    Permission permission(String code);
    /** 与绑定、授权的共享锁协调，调用方须在本地事务中执行。 */
    Permission lock(long permissionId);
    /** 插入权限，数据库唯一键保护编码和资源动作身份。 */
    void insert(Permission permission);
    /** CAS 成功后同步原对象，不提前修改内存状态。 */
    void update(Permission permission, OperationsPermissionRiskLevel riskLevel, PermissionStatus status, Instant updateTime);
}
