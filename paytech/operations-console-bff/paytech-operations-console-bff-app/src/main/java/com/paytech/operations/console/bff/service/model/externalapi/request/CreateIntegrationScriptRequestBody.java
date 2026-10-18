package com.paytech.operations.console.bff.service.model.externalapi.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 在接入方下创建公共脚本。 */
public class CreateIntegrationScriptRequestBody {

    /**
     * 脚本编码
     * <li>接入方内唯一，创建后不可修改。</li>
     */
    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "编码只能由大写字母开头，包含大写字母、数字或下划线，最多 64 字符")
    private final String scriptCode;

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
     * 修改原因
     * <li>去除首尾空白后 1～500 字符，进入审计。</li>
     */
    @NotBlank
    @Size(max = 500)
    private final String reason;

    /** 按 JSON 属性绑定，保留字段的可空语义。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public CreateIntegrationScriptRequestBody(
        @JsonProperty("scriptCode") String scriptCode,
        @JsonProperty("scriptName") String scriptName,
        @JsonProperty("executionStage") String executionStage,
        @JsonProperty("parameterDefinition") List<ScriptParameterBody> parameterDefinition,
        @JsonProperty("sourceCode") String sourceCode,
        @JsonProperty("description") String description,
        @JsonProperty("reason") String reason) {
        this.scriptCode = scriptCode;
        this.scriptName = scriptName;
        this.executionStage = executionStage;
        this.parameterDefinition = parameterDefinition == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(parameterDefinition));
        this.sourceCode = sourceCode;
        this.description = description;
        this.reason = reason;
    }

    /** 脚本编码 */
    public String getScriptCode() {
        return scriptCode;
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

    /** 修改原因 */
    public String getReason() {
        return reason;
    }
}
