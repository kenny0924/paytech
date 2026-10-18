package com.paytech.platform.support.api.externalapi.response;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 接入方详情。 */
public class IntegrationResponseBody {

    /** 接入方编码 */
    private final String integrationCode;

    /** 名称 */
    private final String integrationName;

    /**
     * 说明
     * <li>可为 null。</li>
     */
    private final String description;

    /**
     * 出站地址
     * <li>保持保存时的顺序。</li>
     */
    private final List<IntegrationEndpointDetail> endpoints;

    /** 原子 API 数 */
    private final long apiCount;

    /** 公共脚本数 */
    private final long scriptCount;

    /** 标准 API 实现数 */
    private final long implementationCount;

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
    public IntegrationResponseBody(
        @JsonProperty("integrationCode") String integrationCode,
        @JsonProperty("integrationName") String integrationName,
        @JsonProperty("description") String description,
        @JsonProperty("endpoints") List<IntegrationEndpointDetail> endpoints,
        @JsonProperty("apiCount") long apiCount,
        @JsonProperty("scriptCount") long scriptCount,
        @JsonProperty("implementationCount") long implementationCount,
        @JsonProperty("version") int version,
        @JsonProperty("createTime") Instant createTime,
        @JsonProperty("updateTime") Instant updateTime,
        @JsonProperty("updatedBy") String updatedBy) {
        this.integrationCode = integrationCode;
        this.integrationName = integrationName;
        this.description = description;
        this.endpoints = endpoints == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(endpoints));
        this.apiCount = apiCount;
        this.scriptCount = scriptCount;
        this.implementationCount = implementationCount;
        this.version = version;
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.updatedBy = updatedBy;
    }

    /** 接入方编码 */
    public String getIntegrationCode() {
        return integrationCode;
    }

    /** 名称 */
    public String getIntegrationName() {
        return integrationName;
    }

    /** 说明 */
    public String getDescription() {
        return description;
    }

    /** 出站地址 */
    public List<IntegrationEndpointDetail> getEndpoints() {
        return endpoints;
    }

    /** 原子 API 数 */
    public long getApiCount() {
        return apiCount;
    }

    /** 公共脚本数 */
    public long getScriptCount() {
        return scriptCount;
    }

    /** 标准 API 实现数 */
    public long getImplementationCount() {
        return implementationCount;
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
