package com.paytech.channel.connector.api.externalapi.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 按当前版本物理删除一项外部 API 配置。 */
public class DeleteExternalApiConfigRequestBody {

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
    public DeleteExternalApiConfigRequestBody(
        @JsonProperty(value = "expectedVersion", required = true) int expectedVersion,
        @JsonProperty("reason") String reason) {
        this.expectedVersion = expectedVersion;
        this.reason = reason;
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
