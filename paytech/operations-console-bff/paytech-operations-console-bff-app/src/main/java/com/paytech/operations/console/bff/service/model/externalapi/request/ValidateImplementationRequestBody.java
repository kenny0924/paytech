package com.paytech.operations.console.bff.service.model.externalapi.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import tools.jackson.databind.JsonNode;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 只校验流程实现，不保存。 */
public class ValidateImplementationRequestBody {

    /** 被实现的标准 API 编码 */
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
     * 调用方读取时的版本
     * <li>可选；与当前版本不一致时返回 WARNING 提示基于旧版本。</li>
     */
    @Positive
    private final Integer expectedVersion;

    /** 按 JSON 属性绑定，保留字段的可空语义。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public ValidateImplementationRequestBody(
        @JsonProperty("standardApiCode") String standardApiCode,
        @JsonProperty("flowDefinition") JsonNode flowDefinition,
        @JsonProperty("expectedVersion") Integer expectedVersion) {
        this.standardApiCode = standardApiCode;
        this.flowDefinition = flowDefinition;
        this.expectedVersion = expectedVersion;
    }

    /** 被实现的标准 API 编码 */
    public String getStandardApiCode() {
        return standardApiCode;
    }

    /** 流程定义 */
    public JsonNode getFlowDefinition() {
        return flowDefinition;
    }

    /** 调用方读取时的版本 */
    public Integer getExpectedVersion() {
        return expectedVersion;
    }
}
