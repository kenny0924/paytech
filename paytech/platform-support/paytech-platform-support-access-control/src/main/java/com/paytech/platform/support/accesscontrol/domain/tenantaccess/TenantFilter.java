package com.paytech.platform.support.accesscontrol.domain.tenantaccess;

import com.paytech.platform.support.accesscontrol.domain.enums.AccessTenantType;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantOwnerStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantSecurityStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 分页与计数共用的筛选。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TenantFilter {
    /** 类型筛选。 */
    private AccessTenantType tenantType;

    /** 租户精确筛选。 */
    private Long tenantId;

    /** 来源精确筛选。 */
    private Long ownerRefId;

    /** 已转义名称包含筛选。 */
    private String tenantName;

    /** 来源状态筛选。 */
    private OperationsTenantOwnerStatus ownerStatus;

    /** 安全状态筛选。 */
    private OperationsTenantSecurityStatus securityStatus;
}
