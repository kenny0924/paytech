package com.paytech.platform.support.api.externalapi.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 只校验公共脚本配置，不保存。 */
public class ValidateIntegrationScriptRequestBody {

    /** 脚本编码 */
    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "编码只能由大写字母开头，包含大写字母、数字或下划线，最多 64 字符")
    private final String scriptCode;

    /** 名称 */
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
     * <li>可选；与当前版本不一致时返回 WARNING 提示基于旧版本。</li>
     */
    @Positive
    private final Integer expectedVersion;

    /** 按 JSON 属性绑定，保留字段的可空语义。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public ValidateIntegrationScriptRequestBody(
        @JsonProperty("scriptCode") String scriptCode,
        @JsonProperty("scriptName") String scriptName,
        @JsonProperty("executionStage") String executionStage,
        @JsonProperty("parameterDefinition") List<ScriptParameterBody> parameterDefinition,
        @JsonProperty("sourceCode") String sourceCode,
        @JsonProperty("description") String description,
        @JsonProperty("expectedVersion") Integer expectedVersion) {
        this.scriptCode = scriptCode;
        this.scriptName = scriptName;
        this.executionStage = executionStage;
        this.parameterDefinition = parameterDefinition == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(parameterDefinition));
        this.sourceCode = sourceCode;
        this.description = description;
        this.expectedVersion = expectedVersion;
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

    /** 调用方读取时的版本 */
    public Integer getExpectedVersion() {
        return expectedVersion;
    }
}
