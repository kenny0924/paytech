package com.paytech.channel.connector.api.externalapi.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 按当前版本整体替换流程实现；标准 API 编码来自路径。 */
public class UpdateImplementationRequestBody {

    /**
     * 流程定义
     * <li>结构见流程定义合同；规范化后序列化不超过 65,536 字节。</li>
     */
    @NotNull
    private final JsonNode flowDefinition;

    /**
     * 调用方读取时的版本
     * <li>必须与当前版本一致，否则返回 409。</li>
     */
    @Positive
    private final int expectedVersion;

    /**
     * 修改原因
     * <li>去除首尾空白后 1～500 字符，进入审计。</li>
     */
    @NotBlank
    @Size(max = 500)
    private final String reason;

    /** 按 JSON 属性绑定，保留字段的可空语义。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public UpdateImplementationRequestBody(
        @JsonProperty("flowDefinition") JsonNode flowDefinition,
        @JsonProperty(value = "expectedVersion", required = true) int expectedVersion,
        @JsonProperty("reason") String reason) {
        this.flowDefinition = flowDefinition;
        this.expectedVersion = expectedVersion;
        this.reason = reason;
    }

    /** 流程定义 */
    public JsonNode getFlowDefinition() {
        return flowDefinition;
    }

    /** 调用方读取时的版本 */
    public int getExpectedVersion() {
        return expectedVersion;
    }

    /** 修改原因 */
    public String getReason() {
        return reason;
    }
}
