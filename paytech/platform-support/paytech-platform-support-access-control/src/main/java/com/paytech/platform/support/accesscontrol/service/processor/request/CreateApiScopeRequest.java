package com.paytech.platform.support.accesscontrol.service.processor.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 已校验并规范化的目录创建输入。 */
@Getter
@AllArgsConstructor
public class CreateApiScopeRequest {

    /** 可信 Operations 账号标识。 */
    private final String actor;

    /** 可信 Operations 会话标识。 */
    private final String session;

    /** 不可修改的目录编码。 */
    private final String scopeCode;

    /** 已规范化的展示名称。 */
    private final String displayName;

    /** 已规范化的说明，可空。 */
    private final String description;

    /** 新增原因。 */
    private final String reason;
}
