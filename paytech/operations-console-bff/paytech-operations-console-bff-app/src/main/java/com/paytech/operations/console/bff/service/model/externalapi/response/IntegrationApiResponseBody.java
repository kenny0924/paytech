package com.paytech.operations.console.bff.service.model.externalapi.response;

import java.time.Instant;
import java.util.List;

import tools.jackson.databind.JsonNode;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 原子 API 详情。 */
public class IntegrationApiResponseBody {

    /** 原子 API 编码 */
    private final String apiCode;

    /** 名称 */
    private final String apiName;

    /**
     * 说明
     * <li>可为 null。</li>
     */
    private final String description;

    /** 请求字段 */
    private final List<ApiFieldDetail> requestFields;

    /** 响应字段 */
    private final List<ApiFieldDetail> responseFields;

    /** 执行定义 */
    private final JsonNode definition;

    /**
     * 引用该 API 的实现
     * <li>用于删除保护提示。</li>
     */
    private final List<ApiReferenceDetail> referencedBy;

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
    public IntegrationApiResponseBody(
        @JsonProperty("apiCode") String apiCode,
        @JsonProperty("apiName") String apiName,
        @JsonProperty("description") String description,
        @JsonProperty("requestFields") List<ApiFieldDetail> requestFields,
        @JsonProperty("responseFields") List<ApiFieldDetail> responseFields,
        @JsonProperty("definition") JsonNode definition,
        @JsonProperty("referencedBy") List<ApiReferenceDetail> referencedBy,
        @JsonProperty("version") int version,
        @JsonProperty("createTime") Instant createTime,
        @JsonProperty("updateTime") Instant updateTime,
        @JsonProperty("updatedBy") String updatedBy) {
        this.apiCode = apiCode;
        this.apiName = apiName;
        this.description = description;
        this.requestFields = requestFields == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(requestFields));
        this.responseFields = responseFields == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(responseFields));
        this.definition = definition;
        this.referencedBy = referencedBy == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(referencedBy));
        this.version = version;
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.updatedBy = updatedBy;
    }

    /** 原子 API 编码 */
    public String getApiCode() {
        return apiCode;
    }

    /** 名称 */
    public String getApiName() {
        return apiName;
    }

    /** 说明 */
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

    /** 执行定义 */
    public JsonNode getDefinition() {
        return definition;
    }

    /** 引用该 API 的实现 */
    public List<ApiReferenceDetail> getReferencedBy() {
        return referencedBy;
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
