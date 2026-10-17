package com.paytech.platform.support.accesscontrol.domain.tenantaccess;

import java.time.Instant;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TenantProjection;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TenantProjectionGrant;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TenantProjectionMember;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TenantProjectionRole;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantOwnerStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 来源状态推进及首次 OWNER 角色切换的本地上下文。 */
@Getter
@AllArgsConstructor
public class ProjectTenantStatusContext {
    /** 本次已锁定、尚未推进来源版本的原 Tenant 对象。 */
    private final TenantProjection tenant;

    /** 身份已确认的初始 OWNER 对象。 */
    private final TenantProjectionMember member;

    /** 连续下一版来源序号。 */
    private final int sourceVersion;

    /** 下一版不可变来源摘要。 */
    private final String payloadHash;

    /** 下一版来源业务状态。 */
    private final OperationsTenantOwnerStatus ownerStatus;

    /** 首次 ACTIVE 创建的正式 OWNER 角色，其他状态推进为空。 */
    private final TenantProjectionRole ownerRole;

    /** 正式 OWNER 首次授予，与 ownerRole 同时存在或同时为空。 */
    private final TenantProjectionGrant ownerGrant;

    /** 与 OWNER 角色一同首次创建的管理员、开发者、只读成员系统角色；非首次为空列表。 */
    private final java.util.List<TenantProjectionRole> teamRoles;

    /** 四个系统角色首次获得的固定权限（团队与开发者权限矩阵）；非首次为空列表。 */
    private final java.util.List<com.paytech.platform.support.accesscontrol.core.dal.dataobject.TenantProjectionRolePermission> ownerPermissions;

    /** 本次状态及角色变更的统一 UTC 微秒时间。 */
    private final Instant updateTime;

    /** 只有首次建立正式 OWNER 角色才切换临时授予，不复活被独立撤销的权限。 */
    public boolean activatesOwner() { return ownerRole != null; }
}
