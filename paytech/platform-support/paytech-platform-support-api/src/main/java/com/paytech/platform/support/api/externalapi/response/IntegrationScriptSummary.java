package com.paytech.platform.support.api.externalapi.response;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 公共脚本列表项。 */
public class IntegrationScriptSummary {

    /** 脚本编码 */
    private final String scriptCode;

    /** 名称 */
    private final String scriptName;

    /** 执行阶段 */
    private final String executionStage;

    /**
     * 参数名
     * <li>按声明顺序。</li>
     */
    private final List<String> parameterNames;

    /**
     * 引用该脚本的原子 API 编码
     * <li>按编码排序；非空时不能删除。</li>
     */
    private final List<String> referencedApiCodes;

    /** 当前版本 */
    private final int version;

    /**
     * 最后修改时间
     * <li>UTC。</li>
     */
    private final Instant updateTime;

    /** 按 JSON 属性绑定，保留字段的可空语义。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public IntegrationScriptSummary(
        @JsonProperty("scriptCode") String scriptCode,
        @JsonProperty("scriptName") String scriptName,
        @JsonProperty("executionStage") String executionStage,
        @JsonProperty("parameterNames") List<String> parameterNames,
        @JsonProperty("referencedApiCodes") List<String> referencedApiCodes,
        @JsonProperty("version") int version,
        @JsonProperty("updateTime") Instant updateTime) {
        this.scriptCode = scriptCode;
        this.scriptName = scriptName;
        this.executionStage = executionStage;
        this.parameterNames = parameterNames == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(parameterNames));
        this.referencedApiCodes = referencedApiCodes == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(referencedApiCodes));
        this.version = version;
        this.updateTime = updateTime;
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

    /** 参数名 */
    public List<String> getParameterNames() {
        return parameterNames;
    }

    /** 引用该脚本的原子 API 编码 */
    public List<String> getReferencedApiCodes() {
        return referencedApiCodes;
    }

    /** 当前版本 */
    public int getVersion() {
        return version;
    }

    /** 最后修改时间 */
    public Instant getUpdateTime() {
        return updateTime;
    }
}
