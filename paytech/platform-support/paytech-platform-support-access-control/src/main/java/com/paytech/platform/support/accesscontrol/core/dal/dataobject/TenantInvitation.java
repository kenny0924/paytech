package com.paytech.platform.support.accesscontrol.core.dal.dataobject;

import java.time.Instant;

import com.paytech.platform.support.accesscontrol.domain.enums.TenantInvitationStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.TenantMemberType;

/** 租户成员邀请（t_tenant_invitation）；只保存邮箱 Vault Token 与脱敏值，不保存链接秘密。 */
@lombok.Getter
@lombok.Setter
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
@lombok.experimental.Accessors(fluent = true)
public class TenantInvitation {
    /** 邀请主键。 */
    private long invitationId;

    /** 邀请编号（UUID），不对外返回。 */
    private String invitationNo;

    /** 所属 CLIENT 租户。 */
    private long tenantId;

    /** 受邀邮箱的稳定 Vault Token，与账号 email_vault_token 直接比较。 */
    private String invitedEmailToken;

    /** 受邀邮箱脱敏值。 */
    private String invitedEmailMasked;

    /** 接受后成员的类型，随初始角色确定。 */
    private TenantMemberType memberType;

    /** 同租户的初始系统角色。 */
    private long initialRoleId;

    /** 发起邀请的成员。 */
    private long invitedByMemberId;

    /** 到期时间；重发时重新计算。 */
    private Instant expiresTime;

    /** 持久化状态；PENDING 且已到期对外显示 EXPIRED。 */
    private TenantInvitationStatus status;

    /**
     * 活动邀请占位
     * <li>PENDING 为 1；ACCEPTED、EXPIRED、REVOKED 为 NULL，释放同租户同邮箱的唯一位。</li>
     */
    private Integer activeSlot;

    /** 接受账号；未接受为空。 */
    private Long acceptedAccountId;

    /** 接受后对应的成员；未接受为空。 */
    private Long acceptedMemberId;

    /** 接受时间；未接受为空。 */
    private Instant acceptedTime;

    /** 并发版本。 */
    private int version;

    /** 创建时间。 */
    private Instant createTime;

    /** 不输出邮箱引用。 */
    @Override
    public String toString() {
        return "TenantInvitation[REDACTED]";
    }
}
