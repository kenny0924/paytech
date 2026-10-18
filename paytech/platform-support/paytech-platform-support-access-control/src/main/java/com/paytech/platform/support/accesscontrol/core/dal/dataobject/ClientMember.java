package com.paytech.platform.support.accesscontrol.core.dal.dataobject;

import com.paytech.platform.support.accesscontrol.domain.enums.OperationsMemberStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantOwnerStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantSecurityStatus;

/** CUSTOMER 账号在 CLIENT 租户中的成员及租户当前状态的本库查询投影。 */
@lombok.Getter
@lombok.Setter
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
@lombok.experimental.Accessors(fluent = true)
public class ClientMember {
    /** 成员主键。 */
    private long id;

    /** 所属 CLIENT 租户。 */
    private long tenantId;

    /** 成员账号。 */
    private long accountId;

    /** 租户来源 Client 编号（owner_ref_id）。 */
    private long clientId;

    /** 成员状态。 */
    private OperationsMemberStatus status;

    /** 租户来源业务状态。 */
    private OperationsTenantOwnerStatus ownerStatus;

    /** 租户安全状态。 */
    private OperationsTenantSecurityStatus securityStatus;

    /** 避免成员坐标进入普通日志。 */
    @Override
    public String toString() { return "ClientMember[REDACTED]"; }
}
