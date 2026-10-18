package com.paytech.platform.support.integration.service.processor.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按版本删除外部 API 配置的输入。 */
@Getter
@AllArgsConstructor
public class DeleteExternalApiConfigRequest {

    /** 可信 Operations 账号标识。 */
    private final String actor;

    /** 可信 Operations 会话标识。 */
    private final String session;

    /** 已校验格式的精确编码。 */
    private final String code;

    /** 调用方读取的版本。 */
    private final int expectedVersion;

    /** 删除原因，已去除首尾空白。 */
    private final String reason;
}
