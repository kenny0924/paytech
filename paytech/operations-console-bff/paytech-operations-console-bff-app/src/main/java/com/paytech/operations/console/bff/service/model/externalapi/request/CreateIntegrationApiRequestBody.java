package com.paytech.operations.console.bff.service.model.externalapi.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 在接入方下创建原子 API。 */
public class CreateIntegrationApiRequestBody {

    /**
     * 原子 API 编码
     * <li>接入方内唯一，创建后不可修改。</li>
     */
    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "编码只能由大写字母开头，包含大写字母、数字或下划线，最多 64 字符")
    private final String apiCode;

    /**
     * 名称
     * <li>去除首尾空白后 1～128 字符。</li>
     */
    @NotBlank
    @Size(max = 128)
    private final String apiName;

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
     * 执行定义
     * <li>结构见原子 API 定义合同；规范化后序列化不超过 65,536 字节。</li>
     */
    @NotNull
    private final JsonNode definition;

    /**
     * 修改原因
     * <li>去除首尾空白后 1～500 字符，进入审计。</li>
     */
    @NotBlank
    @Size(max = 500)
    private final String reason;

    /** 按 JSON 属性绑定，保留字段的可空语义。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public CreateIntegrationApiRequestBody(
        @JsonProperty("apiCode") String apiCode,
        @JsonProperty("apiName") String apiName,
        @JsonProperty("description") String description,
        @JsonProperty("requestFields") List<ApiFieldBody> requestFields,
        @JsonProperty("responseFields") List<ApiFieldBody> responseFields,
        @JsonProperty("definition") JsonNode definition,
        @JsonProperty("reason") String reason) {
        this.apiCode = apiCode;
        this.apiName = apiName;
        this.description = description;
        this.requestFields = requestFields == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(requestFields));
        this.responseFields = responseFields == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(responseFields));
        this.definition = definition;
        this.reason = reason;
    }

    /** 原子 API 编码 */
    public String getApiCode() {
        return apiCode;
    }

    /** 名称 */
    public String getApiName() {
        return apiName;
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

    /** 执行定义 */
    public JsonNode getDefinition() {
        return definition;
    }

    /** 修改原因 */
    public String getReason() {
        return reason;
    }
}
