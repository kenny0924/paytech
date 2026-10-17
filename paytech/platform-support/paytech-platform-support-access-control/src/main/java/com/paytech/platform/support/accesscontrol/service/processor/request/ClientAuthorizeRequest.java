package com.paytech.platform.support.accesscontrol.service.processor.request;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** CLIENT 权限判断内部请求。 */
@Getter
@AllArgsConstructor
public final class ClientAuthorizeRequest {
    /** 可信账号编号。 */
    private final long accountId;
    /** 可信会话编号。 */
    private final long sessionId;
    /** 待判断的权限码。 */
    private final List<String> permissionCodes;

    /** 不输出会话坐标。 */
    @Override public String toString() { return "ClientAuthorizeRequest[REDACTED]"; }
}
