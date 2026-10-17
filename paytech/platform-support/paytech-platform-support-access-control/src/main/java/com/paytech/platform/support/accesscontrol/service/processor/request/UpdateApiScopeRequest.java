package com.paytech.platform.support.accesscontrol.service.processor.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 已校验并规范化的目录修改输入。 */
@Getter
@AllArgsConstructor
public class UpdateApiScopeRequest {

    /** 可信 Operations 账号标识。 */
    private final String actor;

    /** 可信 Operations 会话标识。 */
    private final String session;

    /** Path 中的精确目录编码。 */
    private final String scopeCode;

    /** 更新后的展示名称。 */
    private final String displayName;

    /** 更新后的说明，null 表示清空。 */
    private final String description;

    /** 调用方读取的目录版本。 */
    private final int expectedVersion;

    /** 修改原因。 */
    private final String reason;
}
