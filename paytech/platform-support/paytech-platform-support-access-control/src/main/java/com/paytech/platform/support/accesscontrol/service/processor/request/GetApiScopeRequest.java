package com.paytech.platform.support.accesscontrol.service.processor.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按精确编码读取目录定义。 */
@Getter
@AllArgsConstructor
public class GetApiScopeRequest {

    /** 可信 Operations 账号标识。 */
    private final String actor;

    /** 可信 Operations 会话标识。 */
    private final String session;

    /** 不可修改的目录编码。 */
    private final String scopeCode;
}
