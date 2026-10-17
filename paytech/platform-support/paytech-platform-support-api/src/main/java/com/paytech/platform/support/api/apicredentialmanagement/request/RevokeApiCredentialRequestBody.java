package com.paytech.platform.support.api.apicredentialmanagement.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 撤销指定凭证，不级联修改其他凭证或授权行。 */
public class RevokeApiCredentialRequestBody {

    /** 必须匹配当前凭证版本。 */
    @Positive
    private final int expectedVersion;

    /** 撤销原因，去除首尾空白后 1..500 字符。 */
    @NotBlank
    @Size(max = 500)
    private final String reason;

    /** 按既有 JSON 属性绑定，保持字段和可空语义。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public RevokeApiCredentialRequestBody(
        @JsonProperty(value = "expectedVersion", required = true) int expectedVersion,
        @JsonProperty("reason") String reason) {
        this.expectedVersion = expectedVersion;
        this.reason = reason;
    }

    /** 必须匹配当前凭证版本。 */
    public int getExpectedVersion() {
        return expectedVersion;
    }

    /** 撤销原因，去除首尾空白后 1..500 字符。 */
    public String getReason() {
        return reason;
    }
}
