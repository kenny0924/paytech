package com.paytech.platform.support.accesscontrol.domain.clientteam;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 邀请查询结果：邀请、初始角色代码与发起人脱敏邮箱（多表组合）。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TenantInvitationView {
    /** 邀请。 */
    private long invitationId;

    /** 受邀邮箱脱敏值。 */
    private String emailMasked;

    /** 初始角色代码。 */
    private String roleCode;

    /** 存储状态代码；显示状态由调用方按到期时间推导。 */
    private String status;

    /** 到期时间。 */
    private Instant expiresTime;

    /** 发起人账号脱敏邮箱。 */
    private String invitedByEmailMasked;

    /** 创建时间。 */
    private Instant createTime;

    /** 不输出邀请坐标。 */
    @Override
    public String toString() {
        return "TenantInvitationView[REDACTED]";
    }
}
