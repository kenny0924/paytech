package com.paytech.platform.support.accesscontrol.domain.authentication;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;
import com.paytech.platform.support.api.enums.OperationsAccountStatus;
import com.paytech.platform.support.api.enums.OperationsAccountType;

/** 当前账号、凭据、成员与会话的组合查询结果。 */
@Getter
@AllArgsConstructor
@Accessors(fluent = true)
public class OperationsContextView {
    /** 账号 ID 字符串。 */
    private final long accountId;

    /** 会话 ID 字符串。 */
    private final long sessionId;

    /** 账号类型 Code。 */
    private final OperationsAccountType accountType;

    /** 脱敏邮箱；Bootstrap 为空。 */
    private final String emailMasked;

    /** 账号状态 Code。 */
    private final OperationsAccountStatus accountStatus;

    /** 固定 INTERNAL Tenant ID；Bootstrap 为空。 */
    private final Long tenantId;

    /** 成员 ID；Bootstrap 上下文为空。 */
    private final Long memberId;

    /** 是否必须先修改初始密码。 */
    private final boolean passwordChangeRequired;

    /** 是否必须完成首次管理员设置。 */
    private final boolean bootstrapAdministratorRequired;

    /** 当前集成模式标识。 */
    private final String integrationMode;

    /** 避免认证材料进入普通日志。 */
    @Override
    public String toString() { return "OperationsContextView[REDACTED]"; }
}
