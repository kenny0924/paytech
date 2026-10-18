package com.paytech.platform.support.integration.service.processor.request;

import tools.jackson.databind.JsonNode;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 修改渠道标准 API 实现的输入。 */
@Getter
@AllArgsConstructor
public class UpdateImplementationRequest {

    /** 可信 Operations 账号标识。 */
    private final String actor;

    /** 可信 Operations 会话标识。 */
    private final String session;

    /** 路径中的接入方编码。 */
    private final String integrationCode;

    /** 路径中的标准 API 编码。 */
    private final String standardApiCode;

    /** 新流程定义原始 JSON。 */
    private final JsonNode flowDefinition;

    /** 调用方读取的版本。 */
    private final int expectedVersion;

    /** 修改原因。 */
    private final String reason;
}
