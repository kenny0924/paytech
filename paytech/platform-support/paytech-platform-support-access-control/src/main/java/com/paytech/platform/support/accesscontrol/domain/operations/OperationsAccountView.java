package com.paytech.platform.support.accesscontrol.domain.operations;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.accesscontrol.domain.operations.OperationsManagementModels.RoleSummary;

/** Operations 账号、成员、密码状态与当前角色的联合展示视图。 */
@lombok.Getter
public class OperationsAccountView {
    /** Operations 人员账号 ID，十进制字符串。 */
    private final String accountId;

    /** 脱敏邮箱，不包含原始邮箱或 Vault Token。 */
    private final String emailMasked;

    /** 账号状态 Code：ACTIVE、DISABLED 或 CLOSED。 */
    private final String status;

    /** 是否必须先修改初始密码。 */
    private final boolean passwordChangeRequired;

    /** 临时登录锁定截止时刻，UTC；未锁定时为空。 */
    private final Instant lockedUntil;

    /** 当前账号或角色聚合版本。 */
    private final int version;

    /** 当前 INTERNAL Tenant 成员 ID。 */
    private final String memberId;

    /** 当前成员授权聚合版本。 */
    private final int memberVersion;

    /** 人员当前有效角色，无有效角色时为空列表。 */
    private final List<RoleSummary> roles;

    /** 冻结本次查询集合，调用方不能改变已组装的响应事实。 */
    public OperationsAccountView(String accountId, String emailMasked, String status, boolean passwordChangeRequired,
        Instant lockedUntil, int version, String memberId, int memberVersion, List<RoleSummary> roles) {
        this.accountId = accountId;
        this.emailMasked = emailMasked;
        this.status = status;
        this.passwordChangeRequired = passwordChangeRequired;
        this.lockedUntil = lockedUntil;
        this.version = version;
        this.memberId = memberId;
        this.memberVersion = memberVersion;
        this.roles = List.copyOf(roles);
    }
}
