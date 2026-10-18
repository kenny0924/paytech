package com.paytech.platform.support.api.externalapi.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 接入方出站地址及引用它的原子 API。 */
public class IntegrationEndpointDetail {

    /** 地址代码 */
    private final String endpointCode;

    /** 基础地址 */
    private final String baseUrl;

    /** 是否沙箱地址 */
    private final boolean sandbox;

    /**
     * 引用该地址的原子 API 编码
     * <li>按编码排序；非空时不能删除该地址。</li>
     */
    private final List<String> referencedApiCodes;

    /** 按 JSON 属性绑定，保留字段的可空语义。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public IntegrationEndpointDetail(
        @JsonProperty("endpointCode") String endpointCode,
        @JsonProperty("baseUrl") String baseUrl,
        @JsonProperty("sandbox") boolean sandbox,
        @JsonProperty("referencedApiCodes") List<String> referencedApiCodes) {
        this.endpointCode = endpointCode;
        this.baseUrl = baseUrl;
        this.sandbox = sandbox;
        this.referencedApiCodes = referencedApiCodes == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(referencedApiCodes));
    }

    /** 地址代码 */
    public String getEndpointCode() {
        return endpointCode;
    }

    /** 基础地址 */
    public String getBaseUrl() {
        return baseUrl;
    }

    /** 是否沙箱地址 */
    public boolean isSandbox() {
        return sandbox;
    }

    /** 引用该地址的原子 API 编码 */
    public List<String> getReferencedApiCodes() {
        return referencedApiCodes;
    }
}
