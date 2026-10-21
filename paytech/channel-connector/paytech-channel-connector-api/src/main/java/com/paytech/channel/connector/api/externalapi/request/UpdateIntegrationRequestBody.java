package com.paytech.channel.connector.api.externalapi.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 按当前版本修改接入方名称、说明与出站地址；编码来自路径。 */
public class UpdateIntegrationRequestBody {

    /**
     * 名称
     * <li>去除首尾空白后 1～128 字符。</li>
     */
    @NotBlank
    @Size(max = 128)
    private final String integrationName;

    /**
     * 用途说明
     * <li>可为 null；空白视为清空，最多 512 字符。</li>
     */
    @Size(max = 512)
    private final String description;

    /**
     * 出站地址
     * <li>1～16 项；仍被原子 API 引用的地址代码不能删除。</li>
     */
    @NotNull
    @Size(min = 1, max = 16)
    private final List<@Valid @NotNull IntegrationEndpointBody> endpoints;

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
    public UpdateIntegrationRequestBody(
        @JsonProperty("integrationName") String integrationName,
        @JsonProperty("description") String description,
        @JsonProperty("endpoints") List<IntegrationEndpointBody> endpoints,
        @JsonProperty(value = "expectedVersion", required = true) int expectedVersion,
        @JsonProperty("reason") String reason) {
        this.integrationName = integrationName;
        this.description = description;
        this.endpoints = endpoints == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(endpoints));
        this.expectedVersion = expectedVersion;
        this.reason = reason;
    }

    /** 名称 */
    public String getIntegrationName() {
        return integrationName;
    }

    /** 用途说明 */
    public String getDescription() {
        return description;
    }

    /** 出站地址 */
    public List<IntegrationEndpointBody> getEndpoints() {
        return endpoints;
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
