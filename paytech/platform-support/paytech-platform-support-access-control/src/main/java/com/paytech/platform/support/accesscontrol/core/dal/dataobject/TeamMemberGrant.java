package com.paytech.platform.support.accesscontrol.core.dal.dataobject;

import java.time.Instant;

import com.paytech.platform.support.accesscontrol.domain.enums.TenantGrantStatus;

/** 成员的租户角色授予（t_tenant_member_role），附带角色代码。 */
@lombok.Getter
@lombok.Setter
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
@lombok.experimental.Accessors(fluent = true)
public class TeamMemberGrant {
    /** 授予主键。 */
    private long grantId;

    /** 被授予成员。 */
    private long memberId;

    /** 所属租户。 */
    private long tenantId;

    /** 授予的角色。 */
    private long roleId;

    /** 角色代码（只读关联字段）。 */
    private String roleCode;

    /** 生效时间。 */
    private Instant effectiveFrom;

    /** 授予人成员。 */
    private long grantedBy;

    /** 授予状态。 */
    private TenantGrantStatus status;

    /** 并发版本。 */
    private int version;
}
