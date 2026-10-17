package com.paytech.operations.console.bff.service.model.externalapi.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 为接入方创建一个标准 API 的流程实现。 */
public class CreateImplementationRequestBody {

    /**
     * 被实现的标准 API 编码
     * <li>同一接入方对同一标准 API 只有一份实现。</li>
     */
    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "编码只能由大写字母开头，包含大写字母、数字或下划线，最多 64 字符")
    private final String standardApiCode;

    /**
     * 流程定义
     * <li>结构见流程定义合同；规范化后序列化不超过 65,536 字节。</li>
     */
    @NotNull
    private final JsonNode flowDefinition;

    /**
     * 修改原因
     * <li>去除首尾空白后 1～500 字符，进入审计。</li>
     */
    @NotBlank
    @Size(max = 500)
    private final String reason;

    /** 按 JSON 属性绑定，保留字段的可空语义。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public CreateImplementationRequestBody(
        @JsonProperty("standardApiCode") String standardApiCode,
        @JsonProperty("flowDefinition") JsonNode flowDefinition,
        @JsonProperty("reason") String reason) {
        this.standardApiCode = standardApiCode;
        this.flowDefinition = flowDefinition;
        this.reason = reason;
    }

    /** 被实现的标准 API 编码 */
    public String getStandardApiCode() {
        return standardApiCode;
    }

    /** 流程定义 */
    public JsonNode getFlowDefinition() {
        return flowDefinition;
    }

    /** 修改原因 */
    public String getReason() {
        return reason;
    }
}
