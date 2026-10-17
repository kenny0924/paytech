package com.paytech.platform.support.api.apiscopemanagement.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 按当前版本修改名称和说明，编码保持不变。 */
public class UpdateApiScopeRequestBody {

    /** 非空展示名称，去除首尾空白后最多 255 字符。 */
    @NotBlank
    @Size(max = 255)
    private final String displayName;

    /** 必须显式提供，null 或空白表示清空说明，最多 500 字符。 */
    @Size(max = 500)
    private final String description;

    /** 必须匹配当前目录行版本。 */
    @Positive
    private final int expectedVersion;

    /** 修改原因，去除首尾空白后 1..500 字符。 */
    @NotBlank
    @Size(max = 500)
    private final String reason;

    /** 按 JSON 属性绑定，保留既有字段及可空语义。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public UpdateApiScopeRequestBody(
        @JsonProperty("displayName") String displayName,
        @JsonProperty(value = "description", required = true) String description,
        @JsonProperty(value = "expectedVersion", required = true) int expectedVersion,
        @JsonProperty("reason") String reason) {
        this.displayName = displayName;
        this.description = description;
        this.expectedVersion = expectedVersion;
        this.reason = reason;
    }

    /** 非空展示名称，去除首尾空白后最多 255 字符。 */
    public String getDisplayName() {
        return displayName;
    }

    /** 必须显式提供，null 或空白表示清空说明，最多 500 字符。 */
    public String getDescription() {
        return description;
    }

    /** 必须匹配当前目录行版本。 */
    public int getExpectedVersion() {
        return expectedVersion;
    }

    /** 修改原因，去除首尾空白后 1..500 字符。 */
    public String getReason() {
        return reason;
    }
}
