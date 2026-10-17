package com.paytech.platform.support.accesscontrol.domain.clientteam;

import java.time.Duration;
import java.time.Instant;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TenantInvitation;
import com.paytech.platform.support.accesscontrol.domain.enums.TenantInvitationStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.TenantMemberType;
import com.paytech.platform.support.api.enums.ClientTeamRole;

/** 团队与邀请的纯规则；不访问数据库或外部依赖。 */
public final class ClientTeamRules {

    /** 邀请有效期；发起与重发都从当时重新计算。 */
    public static final Duration INVITATION_VALIDITY = Duration.ofDays(7);

    /** 每个工作区同时存在的未到期 PENDING 邀请上限。 */
    public static final int PENDING_LIMIT = 20;

    private ClientTeamRules() {}

    /** 可邀请或改授的角色；所有者角色与未知代码返回参数错误。 */
    public static ClientTeamRole assignableRole(String code) {
        for (ClientTeamRole role : ClientTeamRole.values()) {
            if (role.getCode().equals(code) && role.isAssignable()) {
                return role;
            }
        }
        throw new InvalidParameterException("roleCode 只能为 TENANT_ADMIN、TENANT_DEVELOPER 或 TENANT_VIEWER");
    }

    /** 已知系统角色代码；非系统角色（如历史测试角色）返回空。 */
    public static ClientTeamRole systemRole(String code) {
        for (ClientTeamRole role : ClientTeamRole.values()) {
            if (role.getCode().equals(code)) {
                return role;
            }
        }
        return null;
    }

    /** 授予该角色时成员的类型，按稳定代码对应。 */
    public static TenantMemberType memberType(ClientTeamRole role) {
        for (TenantMemberType type : TenantMemberType.values()) {
            if (type.getCode().equals(role.getMemberType())) {
                return type;
            }
        }
        throw new IllegalArgumentException("角色缺少成员类型：" + role.getCode());
    }

    /** 对外显示状态：PENDING 且已到期显示 EXPIRED，其余按存储状态。 */
    public static TenantInvitationStatus displayStatus(TenantInvitation invitation, Instant now) {
        if (invitation.status() == TenantInvitationStatus.PENDING && !invitation.expiresTime().isAfter(now)) {
            return TenantInvitationStatus.EXPIRED;
        }
        return invitation.status();
    }

    /** 邀请列表筛选：空为 PENDING；只接受 PENDING、EXPIRED、ACCEPTED、REVOKED。 */
    public static TenantInvitationStatus listStatus(String value) {
        if (value == null || value.isBlank()) {
            return TenantInvitationStatus.PENDING;
        }
        for (TenantInvitationStatus status : new TenantInvitationStatus[] {TenantInvitationStatus.PENDING,
            TenantInvitationStatus.EXPIRED, TenantInvitationStatus.ACCEPTED, TenantInvitationStatus.REVOKED}) {
            if (status.getCode().equals(value)) {
                return status;
            }
        }
        throw new InvalidParameterException("status 只能为 PENDING、EXPIRED、ACCEPTED 或 REVOKED");
    }
}
