package com.paytech.platform.support.accesscontrol.domain.operations;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsRole;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按角色原版本原子更新名称与完整权限集合。 */
@Getter
@AllArgsConstructor
public class UpdateOperationsRoleContext {
    /** 当前角色原对象。 */
    private final OperationsRole role;

    /** 固定 INTERNAL Tenant。 */
    private final long tenantId;

    /** 新展示名称。 */
    private final String name;

    /** 原有效权限，用于精确差异保存。 */
    private final List<Long> previousPermissions;

    /** 已验证的新权限集合。 */
    private final List<Long> permissionIds;

    /** 本次保存使用的微秒 UTC 时刻。 */
    private final Instant now;

}
