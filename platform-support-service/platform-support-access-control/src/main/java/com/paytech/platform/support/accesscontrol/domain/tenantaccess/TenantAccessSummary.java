package com.paytech.platform.support.accesscontrol.domain.tenantaccess;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TenantAccess;
import com.paytech.platform.support.accesscontrol.domain.enums.AccessTenantType;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantOwnerStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantSecurityStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 跨模块发布的租户安全摘要，不包含持久化对象。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TenantAccessSummary {
    /** 租户标识。 */
    private Long tenantId;

    /** CLIENT 或 PARTNER。 */
    private AccessTenantType tenantType;

    /** 来源主档标识。 */
    private Long ownerRefId;

    /** 租户名称。 */
    private String tenantName;

    /** 来源业务状态。 */
    private OperationsTenantOwnerStatus ownerStatus;

    /** 平台安全状态。 */
    private OperationsTenantSecurityStatus securityStatus;

    /** 当前并发版本。 */
    private int version;

    /** UTC 创建时间。 */
    private Instant createdAt;

    /** UTC 更新时间。 */
    private Instant updatedAt;

    /** 租户访问限制原因。 */
    private List<TenantAccessReason> restrictionReasons;

    /** 模块边界只复制安全字段，不公开持久化对象。 */
    public TenantAccessSummary(TenantAccess tenant) {
        this(tenant.getTenantId(), tenant.getTenantType(), tenant.getOwnerRefId(), tenant.getTenantName(),
            tenant.getOwnerStatus(), tenant.getSecurityStatus(), tenant.getVersion(), tenant.getCreatedAt(),
            tenant.getUpdatedAt(), tenant.restrictionReasons());
    }
}
