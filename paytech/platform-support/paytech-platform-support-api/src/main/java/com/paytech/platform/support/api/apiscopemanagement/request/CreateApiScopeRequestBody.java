package com.paytech.platform.support.api.apiscopemanagement.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 新增全局 Scope 定义，不授予凭证或创建路由。 */
public class CreateApiScopeRequestBody {

    /** 不可修改的小写 ASCII 能力编码。 */
    @NotBlank
    @Size(max = 128)
    @Pattern(regexp = "[a-z][a-z0-9]*(?:[.:-][a-z0-9]+)*")
    private final String scopeCode;

    /** 非空展示名称，去除首尾空白后最多 255 字符。 */
    @NotBlank
    @Size(max = 255)
    private final String displayName;

    /** 可空说明，null 或空白表示未填写，最多 500 字符。 */
    @Size(max = 500)
    private final String description;

    /** 新增原因，去除首尾空白后 1..500 字符。 */
    @NotBlank
    @Size(max = 500)
    private final String reason;

    /** 按 JSON 属性绑定，保留既有字段及可空语义。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public CreateApiScopeRequestBody(
        @JsonProperty("scopeCode") String scopeCode,
        @JsonProperty("displayName") String displayName,
        @JsonProperty("description") String description,
        @JsonProperty("reason") String reason) {
        this.scopeCode = scopeCode;
        this.displayName = displayName;
        this.description = description;
        this.reason = reason;
    }

    /** 不可修改的小写 ASCII 能力编码。 */
    public String getScopeCode() {
        return scopeCode;
    }

    /** 非空展示名称，去除首尾空白后最多 255 字符。 */
    public String getDisplayName() {
        return displayName;
    }

    /** 可空说明，null 或空白表示未填写，最多 500 字符。 */
    public String getDescription() {
        return description;
    }

    /** 新增原因，去除首尾空白后 1..500 字符。 */
    public String getReason() {
        return reason;
    }
}
