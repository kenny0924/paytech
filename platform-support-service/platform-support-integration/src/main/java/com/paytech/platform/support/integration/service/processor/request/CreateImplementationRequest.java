package com.paytech.platform.support.integration.service.processor.request;

import tools.jackson.databind.JsonNode;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 创建渠道标准 API 实现的输入。 */
@Getter
@AllArgsConstructor
public class CreateImplementationRequest {

    /** 可信 Operations 账号标识。 */
    private final String actor;

    /** 可信 Operations 会话标识。 */
    private final String session;

    /** 路径中的接入方编码。 */
    private final String integrationCode;

    /** 被实现的标准 API 编码。 */
    private final String standardApiCode;

    /** 流程定义原始 JSON。 */
    private final JsonNode flowDefinition;

    /** 创建原因。 */
    private final String reason;
}
