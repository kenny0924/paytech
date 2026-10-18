package com.paytech.platform.support.accesscontrol.domain.tenantaccess;

import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantOwnerStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantSecurityStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 供其他模块使用的 CLIENT 租户概况；不含版本与审计时间。 */
@Getter
@AllArgsConstructor
public class ClientTenant {

    /** Platform 租户 ID。 */
    private final long tenantId;

    /** CCT Client ID。 */
    private final long clientId;

    /** 租户名称（来自 Client 名称投影）。 */
    private final String clientName;

    /** 来源业务状态。 */
    private final OperationsTenantOwnerStatus ownerStatus;

    /** Platform 安全状态。 */
    private final OperationsTenantSecurityStatus securityStatus;
}
