package com.paytech.platform.support.accesscontrol.domain.operations;

import java.time.Instant;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsRole;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 逻辑删除角色并撤销全部权限。 */
@Getter
@AllArgsConstructor
public class DeleteOperationsRoleContext {
    /** 当前可删除的角色原对象。 */
    private final OperationsRole role;

    /** 固定 INTERNAL Tenant。 */
    private final long tenantId;

    /** 本次保存使用的微秒 UTC 时刻。 */
    private final Instant now;

}
