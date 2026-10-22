package com.paytech.channel.connector.protocol.service.processor.request;

import tools.jackson.databind.JsonNode;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 创建渠道标准 API 实现的输入。 */
@Getter
@AllArgsConstructor
public class CreateImplementationRequest {

    /** 操作者 Account ID（X-Gateway-Actor-Subject，规范正十进制）。 */
    private final long actorId;

    /** 路径中的渠道编码。 */
    private final String integrationCode;

    /** 被实现的标准 API 编码。 */
    private final String standardApiCode;

    /** 流程定义原始 JSON。 */
    private final JsonNode flowDefinition;

    /** 创建原因。 */
    private final String reason;
}
