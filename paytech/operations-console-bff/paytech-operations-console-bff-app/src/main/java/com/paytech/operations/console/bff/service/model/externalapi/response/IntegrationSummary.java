package com.paytech.operations.console.bff.service.model.externalapi.response;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 接入方列表项。 */
public class IntegrationSummary {

    /** 接入方编码 */
    private final String integrationCode;

    /** 名称 */
    private final String integrationName;

    /** 出站地址数 */
    private final int endpointCount;

    /** 原子 API 数 */
    private final long apiCount;

    /** 公共脚本数 */
    private final long scriptCount;

    /**
     * 已实现的标准 API
     * <li>按标准 API 编码排序。</li>
     */
    private final List<StandardApiReference> implementedStandardApis;

    /** 当前版本 */
    private final int version;

    /**
     * 最后修改时间
     * <li>UTC。</li>
     */
    private final Instant updateTime;

    /** 按 JSON 属性绑定，保留字段的可空语义。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public IntegrationSummary(
        @JsonProperty("integrationCode") String integrationCode,
        @JsonProperty("integrationName") String integrationName,
        @JsonProperty("endpointCount") int endpointCount,
        @JsonProperty("apiCount") long apiCount,
        @JsonProperty("scriptCount") long scriptCount,
        @JsonProperty("implementedStandardApis") List<StandardApiReference> implementedStandardApis,
        @JsonProperty("version") int version,
        @JsonProperty("updateTime") Instant updateTime) {
        this.integrationCode = integrationCode;
        this.integrationName = integrationName;
        this.endpointCount = endpointCount;
        this.apiCount = apiCount;
        this.scriptCount = scriptCount;
        this.implementedStandardApis = implementedStandardApis == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(implementedStandardApis));
        this.version = version;
        this.updateTime = updateTime;
    }

    /** 接入方编码 */
    public String getIntegrationCode() {
        return integrationCode;
    }

    /** 名称 */
    public String getIntegrationName() {
        return integrationName;
    }

    /** 出站地址数 */
    public int getEndpointCount() {
        return endpointCount;
    }

    /** 原子 API 数 */
    public long getApiCount() {
        return apiCount;
    }

    /** 公共脚本数 */
    public long getScriptCount() {
        return scriptCount;
    }

    /** 已实现的标准 API */
    public List<StandardApiReference> getImplementedStandardApis() {
        return implementedStandardApis;
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
