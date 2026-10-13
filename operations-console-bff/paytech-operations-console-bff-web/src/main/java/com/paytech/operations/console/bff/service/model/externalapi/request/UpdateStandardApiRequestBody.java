package com.paytech.operations.console.bff.service.model.externalapi.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 按当前版本整体修改标准 API 的名称、说明与字段；编码来自路径。 */
public class UpdateStandardApiRequestBody {

    /**
     * 名称
     * <li>去除首尾空白后 1～128 字符。</li>
     */
    @NotBlank
    @Size(max = 128)
    private final String standardApiName;

    /**
     * 用途说明
     * <li>可为 null；空白视为清空，最多 512 字符。</li>
     */
    @Size(max = 512)
    private final String description;

    /**
     * 请求字段
     * <li>必传；空数组表示清空。数组顺序即显示顺序，每个方向最多 128 项。</li>
     */
    @NotNull
    @Size(max = 128)
    private final List<@Valid @NotNull ApiFieldBody> requestFields;

    /**
     * 响应字段
     * <li>必传；空数组表示清空。数组顺序即显示顺序，每个方向最多 128 项。</li>
     */
    @NotNull
    @Size(max = 128)
    private final List<@Valid @NotNull ApiFieldBody> responseFields;

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
    public UpdateStandardApiRequestBody(
        @JsonProperty("standardApiName") String standardApiName,
        @JsonProperty("description") String description,
        @JsonProperty("requestFields") List<ApiFieldBody> requestFields,
        @JsonProperty("responseFields") List<ApiFieldBody> responseFields,
        @JsonProperty(value = "expectedVersion", required = true) int expectedVersion,
        @JsonProperty("reason") String reason) {
        this.standardApiName = standardApiName;
        this.description = description;
        this.requestFields = requestFields == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(requestFields));
        this.responseFields = responseFields == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(responseFields));
        this.expectedVersion = expectedVersion;
        this.reason = reason;
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
    public List<ApiFieldBody> getRequestFields() {
        return requestFields;
    }

    /** 响应字段 */
    public List<ApiFieldBody> getResponseFields() {
        return responseFields;
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
