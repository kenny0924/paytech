package com.paytech.channel.connector.protocol.service.processor.request;

import tools.jackson.databind.JsonNode;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 修改渠道标准 API 实现的输入。 */
@Getter
@AllArgsConstructor
public class UpdateImplementationRequest {

    /** 操作者 Account ID（X-Gateway-Actor-Subject，规范正十进制）。 */
    private final long actorId;

    /** 路径中的渠道编码。 */
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
