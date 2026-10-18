package com.paytech.platform.support.accesscontrol.domain.tenantaccess;

import java.time.Instant;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TenantAccess;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantSecurityStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 租户安全状态变更的完整本地上下文。 */
@Getter
@AllArgsConstructor
public class ChangeTenantSecurityStatusContext {
    /** 已经锁定并验证归属与版本的原租户对象。 */
    private final TenantAccess tenant;

    /** 目标安全状态。 */
    private final OperationsTenantSecurityStatus securityStatus;

    /** 本次更新的 UTC 微秒时刻。 */
    private final Instant updateTime;
}
