package com.paytech.channel.connector.protocol.service.processor.request;

import java.util.List;

import com.paytech.channel.connector.protocol.domain.FieldDefinition;
import tools.jackson.databind.JsonNode;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 只校验原子 API 配置的输入。 */
@Getter
@AllArgsConstructor
public class ValidateIntegrationApiRequest {

    /** 路径中的接入方编码。 */
    private final String integrationCode;

    /** 原子 API 编码。 */
    private final String code;

    /** 名称。 */
    private final String name;

    /** 说明。 */
    private final String description;

    /** 请求字段。 */
    private final List<FieldDefinition> requestFields;

    /** 响应字段。 */
    private final List<FieldDefinition> responseFields;

    /** 执行定义原始 JSON。 */
    private final JsonNode definition;

    /** 可选的读取版本，null 表示按新建校验。 */
    private final Integer expectedVersion;
}
