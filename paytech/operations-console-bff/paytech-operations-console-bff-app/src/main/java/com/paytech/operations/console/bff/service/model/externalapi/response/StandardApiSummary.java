package com.paytech.operations.console.bff.service.model.externalapi.response;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 标准 API 列表项。 */
public class StandardApiSummary {

    /** 标准 API 编码 */
    private final String standardApiCode;

    /** 名称 */
    private final String standardApiName;

    /** 请求字段数 */
    private final int requestFieldCount;

    /** 响应字段数 */
    private final int responseFieldCount;

    /**
     * 已实现该标准 API 的接入方
     * <li>按接入方编码排序。</li>
     */
    private final List<IntegrationReference> implementedBy;

    /** 当前版本 */
    private final int version;

    /**
     * 最后修改时间
     * <li>UTC。</li>
     */
    private final Instant updateTime;

    /** 按 JSON 属性绑定，保留字段的可空语义。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public StandardApiSummary(@JsonProperty("standardApiCode") String standardApiCode,
        @JsonProperty("standardApiName") String standardApiName,
        @JsonProperty("requestFieldCount") int requestFieldCount,
        @JsonProperty("responseFieldCount") int responseFieldCount,
        @JsonProperty("implementedBy") List<IntegrationReference> implementedBy, @JsonProperty("version") int version,
        @JsonProperty("updateTime") Instant updateTime) {
        this.standardApiCode = standardApiCode;
        this.standardApiName = standardApiName;
        this.requestFieldCount = requestFieldCount;
        this.responseFieldCount = responseFieldCount;
        this.implementedBy = implementedBy == null ? null
            : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(implementedBy));
        this.version = version;
        this.updateTime = updateTime;
    }

    /** 标准 API 编码 */
    public String getStandardApiCode() {
        return standardApiCode;
    }

    /** 名称 */
    public String getStandardApiName() {
        return standardApiName;
    }

    /** 请求字段数 */
    public int getRequestFieldCount() {
        return requestFieldCount;
    }

    /** 响应字段数 */
    public int getResponseFieldCount() {
        return responseFieldCount;
    }

    /** 已实现该标准 API 的接入方 */
    public List<IntegrationReference> getImplementedBy() {
        return implementedBy;
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
