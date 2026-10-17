package com.paytech.platform.support.integration.service.processor.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按编码读取单个外部 API 配置的输入。 */
@Getter
@AllArgsConstructor
public class GetExternalApiConfigRequest {

    /** 可信 Operations 账号标识。 */
    private final String actor;

    /** 可信 Operations 会话标识。 */
    private final String session;

    /** 已校验格式的精确编码。 */
    private final String code;
}
