package com.paytech.platform.support.accesscontrol.service.processor.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 可信 Operations 身份及待授权路由；HTTP 字段约束由入口触发。 */
@Getter
@AllArgsConstructor
public class AuthorizeOperationsRequest {
    /** Gateway 可信账号标识，当前有效性由会话规则确认。 */
    private final String accountId;

    /** Gateway 可信会话标识。 */
    private final String sessionId;

    /** Gateway 可信路由代码，不从浏览器权限声明推导。 */
    private final String routeCode;
}
