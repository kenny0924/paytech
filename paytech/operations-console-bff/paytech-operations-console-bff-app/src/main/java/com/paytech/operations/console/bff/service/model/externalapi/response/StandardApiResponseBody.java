package com.paytech.operations.console.bff.service.model.externalapi.response;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 标准 API 详情。 */
public class StandardApiResponseBody {

    /** 标准 API 编码 */
    private final String standardApiCode;

    /** 名称 */
    private final String standardApiName;

    /**
     * 用途说明
     * <li>可为 null。</li>
     */
    private final String description;

    /** 请求字段 */
    private final List<ApiFieldDetail> requestFields;

    /** 响应字段 */
    private final List<ApiFieldDetail> responseFields;

    /**
     * 已实现该标准 API 的接入方
     * <li>用于删除保护提示。</li>
     */
    private final List<IntegrationReference> implementations;

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
    public StandardApiResponseBody(
        @JsonProperty("standardApiCode") String standardApiCode,
        @JsonProperty("standardApiName") String standardApiName,
        @JsonProperty("description") String description,
        @JsonProperty("requestFields") List<ApiFieldDetail> requestFields,
        @JsonProperty("responseFields") List<ApiFieldDetail> responseFields,
        @JsonProperty("implementations") List<IntegrationReference> implementations,
        @JsonProperty("version") int version,
        @JsonProperty("createTime") Instant createTime,
        @JsonProperty("updateTime") Instant updateTime,
        @JsonProperty("updatedBy") String updatedBy) {
        this.standardApiCode = standardApiCode;
        this.standardApiName = standardApiName;
        this.description = description;
        this.requestFields = requestFields == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(requestFields));
        this.responseFields = responseFields == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(responseFields));
        this.implementations = implementations == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(implementations));
        this.version = version;
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.updatedBy = updatedBy;
    }

    /** 标准 API 编码 */
    public String getStandardApiCode() {
        return standardApiCode;
    }

    /** 名称 */
    public String getStandardApiName() {
        return standardApiName;
    }

    /** 用途说明 */
    public String getDescription() {
        return description;
    }

    /** 请求字段 */
    public List<ApiFieldDetail> getRequestFields() {
        return requestFields;
    }

    /** 响应字段 */
    public List<ApiFieldDetail> getResponseFields() {
        return responseFields;
    }

    /** 已实现该标准 API 的接入方 */
    public List<IntegrationReference> getImplementations() {
        return implementations;
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
