package com.paytech.channel.connector.protocol.service.processor.request;

import tools.jackson.databind.JsonNode;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 只校验渠道标准 API 实现的输入。 */
@Getter
@AllArgsConstructor
public class ValidateImplementationRequest {

    /** 路径中的渠道编码。 */
    private final String integrationCode;

    /** 被实现的标准 API 编码。 */
    private final String standardApiCode;

    /** 流程定义原始 JSON。 */
    private final JsonNode flowDefinition;

    /** 可选的读取版本，null 表示按新建校验。 */
    private final Integer expectedVersion;
}
