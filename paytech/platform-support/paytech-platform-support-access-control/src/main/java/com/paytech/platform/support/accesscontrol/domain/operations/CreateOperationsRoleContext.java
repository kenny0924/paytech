package com.paytech.platform.support.accesscontrol.domain.operations;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsRole;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 创建角色及权限关联的完整上下文。 */
@Getter
@AllArgsConstructor
public class CreateOperationsRoleContext {
    /** 待创建的角色原对象。 */
    private final OperationsRole role;

    /** 固定 INTERNAL Tenant。 */
    private final long tenantId;

    /** 已验证的新权限集合。 */
    private final List<Long> permissionIds;

    /** 本次保存使用的微秒 UTC 时刻。 */
    private final Instant now;

}
