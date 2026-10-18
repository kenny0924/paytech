package com.paytech.platform.support.accesscontrol.core.dal.dataobject;

import java.time.Instant;

import com.paytech.platform.support.accesscontrol.domain.enums.OperationsMemberStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.TenantMemberType;

/** CLIENT 工作区成员（t_tenant_member），附带账号脱敏邮箱用于响应；团队管理与接受邀请使用。 */
@lombok.Getter
@lombok.Setter
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
@lombok.experimental.Accessors(fluent = true)
public class TeamMember {
    /** 成员主键。 */
    private long memberId;

    /** 所属租户。 */
    private long tenantId;

    /** 成员账号。 */
    private long accountId;

    /** 成员类型。 */
    private TenantMemberType memberType;

    /** 成员状态。 */
    private OperationsMemberStatus status;

    /** 邀请人成员；入驻申请人为空。 */
    private Long invitedBy;

    /** 账号邮箱脱敏值（只读关联字段）。 */
    private String emailMasked;

    /** 并发版本。 */
    private int version;

    /** 创建时间，作为加入时间。 */
    private Instant createTime;

    /** 不输出成员坐标。 */
    @Override
    public String toString() {
        return "TeamMember[REDACTED]";
    }
}
