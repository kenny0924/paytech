package com.paytech.platform.support.accesscontrol.service.processor.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 资源用例输入，不携带 HTTP 请求幂等身份。 */
@Getter
@AllArgsConstructor
public class QueryConsoleResourceCapabilitiesRequest {
    /** 可信 Operations 操作者。 */
    private final String actor;

    /** 当前会话。 */
    private final String session;

}
