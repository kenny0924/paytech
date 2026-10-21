package com.paytech.channel.connector.api.externalapi.response;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 公共脚本详情。 */
public class IntegrationScriptResponseBody {

    /** 脚本编码 */
    private final String scriptCode;

    /** 名称 */
    private final String scriptName;

    /** 执行阶段 */
    private final String executionStage;

    /** 参数声明 */
    private final List<ScriptParameterDetail> parameterDefinition;

    /** 脚本源码 */
    private final String sourceCode;

    /**
     * 说明
     * <li>可为 null。</li>
     */
    private final String description;

    /** 引用该脚本的原子 API 编码 */
    private final List<String> referencedApiCodes;

    /** 当前版本 */
    private final int version;

    /**
     * 创建时间
     * <li>UTC。</li>
     */
    private final Instant createTime;

    /**
     * 最后修改时间
     * <li>UTC。</li>
     */
    private final Instant updateTime;

    /**
     * 最后修改人
     * <li>Operations 账号标识，十进制字符串。</li>
     */
    private final String updatedBy;

    /** 按 JSON 属性绑定，保留字段的可空语义。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public IntegrationScriptResponseBody(
        @JsonProperty("scriptCode") String scriptCode,
        @JsonProperty("scriptName") String scriptName,
        @JsonProperty("executionStage") String executionStage,
        @JsonProperty("parameterDefinition") List<ScriptParameterDetail> parameterDefinition,
        @JsonProperty("sourceCode") String sourceCode,
        @JsonProperty("description") String description,
        @JsonProperty("referencedApiCodes") List<String> referencedApiCodes,
        @JsonProperty("version") int version,
        @JsonProperty("createTime") Instant createTime,
        @JsonProperty("updateTime") Instant updateTime,
        @JsonProperty("updatedBy") String updatedBy) {
        this.scriptCode = scriptCode;
        this.scriptName = scriptName;
        this.executionStage = executionStage;
        this.parameterDefinition = parameterDefinition == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(parameterDefinition));
        this.sourceCode = sourceCode;
        this.description = description;
        this.referencedApiCodes = referencedApiCodes == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(referencedApiCodes));
        this.version = version;
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.updatedBy = updatedBy;
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
    public List<ScriptParameterDetail> getParameterDefinition() {
        return parameterDefinition;
    }

    /** 脚本源码 */
    public String getSourceCode() {
        return sourceCode;
    }

    /** 说明 */
    public String getDescription() {
        return description;
    }

    /** 引用该脚本的原子 API 编码 */
    public List<String> getReferencedApiCodes() {
        return referencedApiCodes;
    }

    /** 当前版本 */
    public int getVersion() {
        return version;
    }

    /** 创建时间 */
    public Instant getCreateTime() {
        return createTime;
    }

    /** 最后修改时间 */
    public Instant getUpdateTime() {
        return updateTime;
    }

    /** 最后修改人 */
    public String getUpdatedBy() {
        return updatedBy;
    }
}
