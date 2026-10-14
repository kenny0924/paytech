package com.paytech.platform.support.integration.domain.execution;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

    /** decimalEncoding 为 NUMBER：与渠道往来的报文中 DECIMAL 是 JSON 数字（默认 STRING 为十进制字符串）。 */
    public boolean isDecimalAsNumber() {
        return "NUMBER".equals(definition.path("decimalEncoding").asString(""));
    }

    /** cardFields 声明的卡字段键 BODY:&lt;path&gt;；只能由卡值句柄赋值（PayerMax 卡直付对接技术方案第 3.2 节）。 */
    public Set<String> cardFieldKeys() {
        var keys = new LinkedHashSet<String>();
        definition.path("cardFields").forEach(key -> keys.add(key.asString("")));
        return keys;
    }
}
