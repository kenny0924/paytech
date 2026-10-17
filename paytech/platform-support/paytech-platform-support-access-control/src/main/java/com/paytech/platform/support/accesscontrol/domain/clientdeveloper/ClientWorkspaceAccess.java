package com.paytech.platform.support.accesscontrol.domain.clientdeveloper;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 已按当前会话重证的 CLIENT 工作区访问坐标；只来自可信会话，不接受调用方指定。 */
@Getter
@AllArgsConstructor
public final class ClientWorkspaceAccess {
    /** 当前账号。 */
    private final long accountId;

    /** 当前租户上下文会话。 */
    private final long sessionId;

    /** 当前工作区租户。 */
    private final long tenantId;

    /** 当前成员。 */
    private final long memberId;

    /** 工作区来源 Client 编号。 */
    private final long clientId;

    /** 避免坐标进入普通日志。 */
    @Override
    public String toString() {
        return "ClientWorkspaceAccess[REDACTED]";
    }
}
