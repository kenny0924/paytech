package com.paytech.platform.support.api.externalapi.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 只校验原子 API 配置，不保存。 */
public class ValidateIntegrationApiRequestBody {

    /** 原子 API 编码 */
    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "编码只能由大写字母开头，包含大写字母、数字或下划线，最多 64 字符")
    private final String apiCode;

    /** 名称 */
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
     * 调用方读取时的版本
     * <li>可选；与当前版本不一致时返回 WARNING 提示基于旧版本。</li>
     */
    @Positive
    private final Integer expectedVersion;

    /** 按 JSON 属性绑定，保留字段的可空语义。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public ValidateIntegrationApiRequestBody(
        @JsonProperty("apiCode") String apiCode,
        @JsonProperty("apiName") String apiName,
        @JsonProperty("description") String description,
        @JsonProperty("requestFields") List<ApiFieldBody> requestFields,
        @JsonProperty("responseFields") List<ApiFieldBody> responseFields,
        @JsonProperty("definition") JsonNode definition,
        @JsonProperty("expectedVersion") Integer expectedVersion) {
        this.apiCode = apiCode;
        this.apiName = apiName;
        this.description = description;
        this.requestFields = requestFields == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(requestFields));
        this.responseFields = responseFields == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(responseFields));
        this.definition = definition;
        this.expectedVersion = expectedVersion;
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

    /** 调用方读取时的版本 */
    public Integer getExpectedVersion() {
        return expectedVersion;
    }
}
