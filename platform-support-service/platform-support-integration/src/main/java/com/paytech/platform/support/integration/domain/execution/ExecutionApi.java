package com.paytech.platform.support.integration.domain.execution;

import java.util.List;

import com.paytech.platform.support.integration.domain.FieldDefinition;

import lombok.AllArgsConstructor;
import lombok.Getter;

import tools.jackson.databind.JsonNode;

/** 流程引用的一个原子 API 在本次调用中的只读快照。 */
@Getter
@AllArgsConstructor
public class ExecutionApi {

    /** 原子 API 编码。 */
    private final String code;

    /** 已通过运行前校验的 definition_json。 */
    private final JsonNode definition;

    /** 请求字段。 */
    private final List<FieldDefinition> requestFields;

    /** 响应字段。 */
    private final List<FieldDefinition> responseFields;
}
