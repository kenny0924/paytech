package com.paytech.platform.support.integration.service.processor.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 只需操作者身份的外部 API 配置查询输入。 */
@Getter
@AllArgsConstructor
public class ExternalApiActorRequest {

    /** 可信 Operations 账号标识。 */
    private final String actor;

    /** 可信 Operations 会话标识。 */
    private final String session;
}
