package com.paytech.platform.support.accesscontrol.core.dal.dataobject;

import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantOwnerStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantSecurityStatus;

/** 接受邀请时读取的 CLIENT 租户状态（t_access_tenant，只读）。 */
@lombok.Getter
@lombok.Setter
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
@lombok.experimental.Accessors(fluent = true)
public class TeamTenant {
    /** 租户主键。 */
    private long tenantId;

    /** 来源 Client 编号（owner_ref_id）。 */
    private long clientId;

    /** 来源业务状态。 */
    private OperationsTenantOwnerStatus ownerStatus;

    /** 平台安全状态。 */
    private OperationsTenantSecurityStatus securityStatus;

    /** 来源状态与安全状态均为 ACTIVE 才可加入。 */
    public boolean accessible() {
        return ownerStatus == OperationsTenantOwnerStatus.ACTIVE
            && securityStatus == OperationsTenantSecurityStatus.ACTIVE;
    }
}
