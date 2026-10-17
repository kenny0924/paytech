package com.paytech.platform.support.accesscontrol.domain.tenantaccess;

import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantSecurityStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 稳定命令结果。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TenantSecurityStatusResult {
    /** 目标租户。 */
    private long tenantId;

    /** 当次安全状态。 */
    private OperationsTenantSecurityStatus securityStatus;

    /** 当次结果版本。 */
    private int version;
}
