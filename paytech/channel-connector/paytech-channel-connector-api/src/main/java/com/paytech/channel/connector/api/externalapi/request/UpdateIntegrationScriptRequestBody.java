package com.paytech.channel.connector.api.externalapi.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 按当前版本整体修改公共脚本；编码来自路径。 */
public class UpdateIntegrationScriptRequestBody {

    /**
     * 名称
     * <li>去除首尾空白后 1～128 字符。</li>
     */
    @NotBlank
    @Size(max = 128)
    private final String scriptName;

    /**
     * 执行阶段
     * <li>允许值：REQUEST、RESPONSE。</li>
     */
    @NotBlank
    @Pattern(regexp = "REQUEST|RESPONSE", message = "执行阶段只允许 REQUEST、RESPONSE")
    private final String executionStage;

    /**
     * 参数声明
     * <li>必传，最多 32 项；空数组表示无参数。</li>
     */
    @NotNull
    @Size(max = 32)
    private final List<@Valid @NotNull ScriptParameterBody> parameterDefinition;

    /**
     * Aviator 脚本源码
     * <li>非空，UTF-8 不超过 32 KiB。</li>
     */
    @NotBlank
    @Size(max = 32768)
    private final String sourceCode;

    /**
     * 用途说明
     * <li>可为 null；空白视为清空，最多 512 字符。</li>
     */
    @Size(max = 512)
    private final String description;

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
    public UpdateIntegrationScriptRequestBody(
        @JsonProperty("scriptName") String scriptName,
        @JsonProperty("executionStage") String executionStage,
        @JsonProperty("parameterDefinition") List<ScriptParameterBody> parameterDefinition,
        @JsonProperty("sourceCode") String sourceCode,
        @JsonProperty("description") String description,
        @JsonProperty(value = "expectedVersion", required = true) int expectedVersion,
        @JsonProperty("reason") String reason) {
        this.scriptName = scriptName;
        this.executionStage = executionStage;
        this.parameterDefinition = parameterDefinition == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(parameterDefinition));
        this.sourceCode = sourceCode;
        this.description = description;
        this.expectedVersion = expectedVersion;
        this.reason = reason;
    }

    /** 名称 */
    public String getScriptName() {
        return scriptName;
    }

    /** 执行阶段 */
    public String getExecutionStage() {
        return executionStage;
    }

    /** 参数声明 */
    public List<ScriptParameterBody> getParameterDefinition() {
        return parameterDefinition;
    }

    /** Aviator 脚本源码 */
    public String getSourceCode() {
        return sourceCode;
    }

    /** 用途说明 */
    public String getDescription() {
        return description;
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
