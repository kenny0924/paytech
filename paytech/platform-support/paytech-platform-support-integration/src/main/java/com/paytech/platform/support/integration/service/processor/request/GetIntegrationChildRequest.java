package com.paytech.platform.support.integration.service.processor.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按编码读取接入方下级配置的输入。 */
@Getter
@AllArgsConstructor
public class GetIntegrationChildRequest {

    /** 可信 Operations 账号标识。 */
    private final String actor;

    /** 可信 Operations 会话标识。 */
    private final String session;

    /** 路径中的接入方编码。 */
    private final String integrationCode;

    /** 已校验格式的下级配置编码。 */
    private final String code;
}
