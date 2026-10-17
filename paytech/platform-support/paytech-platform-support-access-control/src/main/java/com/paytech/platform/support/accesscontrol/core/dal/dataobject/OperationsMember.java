package com.paytech.platform.support.accesscontrol.core.dal.dataobject;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsMemberStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantOwnerStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantSecurityStatus;

/** Operations 本库事实；由 Repository 在写入成功后同步原对象。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public class OperationsMember {
    /** 成员主键。 */
    private long id;

    /** 固定INTERNAL Tenant。 */
    private long tenantId;

    /** 所属Operations账号。 */
    private long accountId;

    /** 成员状态。 */
    private OperationsMemberStatus status;

    /** Tenant业务状态。 */
    private OperationsTenantOwnerStatus ownerStatus;

    /** Tenant安全状态。 */
    private OperationsTenantSecurityStatus securityStatus;

    /** 成员聚合并发版本。 */
    private int version;

    /** 避免认证材料进入普通日志。 */
    @Override
    public String toString() { return "OperationsMember[REDACTED]"; }
}
