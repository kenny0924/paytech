package com.paytech.platform.support.accesscontrol.core.dal.dataobject;

import java.time.Instant;
import java.util.List;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.platform.support.accesscontrol.domain.enums.AccessTenantType;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantOwnerStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantSecurityStatus;
import com.paytech.platform.support.accesscontrol.domain.tenantaccess.TenantAccessReason;
import com.paytech.platform.support.accesscontrol.domain.tenantaccess.TenantSecurityStatusResult;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 租户访问的本库安全查询投影。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TenantAccess {
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

    /** 本入口只管理客户与合作伙伴；持久化字段由严格枚举映射保证完整。 */
    public void requireManagedTenant() {
        if (tenantType == AccessTenantType.INTERNAL) throw new FailException(CommonErrorCode.NOT_FOUND);
        if (tenantId <= 0 || ownerRefId <= 0 || version <= 0)
            throw new ErrorException("租户成员授权记录不一致，请联系管理员检查");
    }

    /** 先核对原版本，同状态请求也不能绕过并发保护。 */
    public void requireVersion(int expectedVersion) {
        if (version != expectedVersion)
            throw new ConcurrentRequestException("租户已被其他操作修改，请刷新后重试");
    }

    /** 只有真实状态变化才需要推进版本，不允许整数溢出。 */
    public void requireVersionAdvance() {
        if (version == Integer.MAX_VALUE)
            throw new ConcurrentRequestException("租户已被其他操作修改，请刷新后重试");
    }

    /** 两种状态独立解释；恢复安全状态不会恢复来源业务。 */
    public List<TenantAccessReason> restrictionReasons() {
        var reasons = new java.util.ArrayList<TenantAccessReason>();
        if (ownerStatus != OperationsTenantOwnerStatus.ACTIVE) reasons.add(TenantAccessReason.TENANT_OWNER_INACTIVE);
        if (securityStatus == OperationsTenantSecurityStatus.SUSPENDED) reasons.add(TenantAccessReason.TENANT_SECURITY_SUSPENDED);
        return List.copyOf(reasons);
    }

    /** 响应使用本次原对象的已确认状态，不重新查询可变事实。 */
    public TenantSecurityStatusResult securityStatusResult() {
        return new TenantSecurityStatusResult(tenantId, securityStatus, version);
    }
}
