package com.paytech.platform.support.accesscontrol.service.processor.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按当前 Operations 身份查询可见导航。 */
@Getter
@AllArgsConstructor
public class QueryOperationsNavigationRequest {
    /** Gateway 可信账号标识，当前有效性由会话规则确认。 */
    private final String accountId;

    /** Gateway 可信会话标识。 */
    private final String sessionId;
}
