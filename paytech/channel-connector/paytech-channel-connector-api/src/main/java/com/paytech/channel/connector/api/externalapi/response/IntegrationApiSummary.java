package com.paytech.channel.connector.api.externalapi.response;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 原子 API 列表项。 */
public class IntegrationApiSummary {

    /** 原子 API 编码 */
    private final String apiCode;

    /** 名称 */
    private final String apiName;

    /** HTTP 方法 */
    private final String method;

    /** 出站地址代码 */
    private final String endpointCode;

    /** 路径模板 */
    private final String pathTemplate;

    /** 请求阶段脚本数 */
    private final int requestScriptCount;

    /** 响应阶段脚本数 */
    private final int responseScriptCount;

    /**
     * 引用该 API 的标准 API 编码
     * <li>按编码排序；非空时不能删除。</li>
     */
    private final List<String> referencedBy;

    /** 当前版本 */
    private final int version;

    /**
     * 最后修改时间
     * <li>UTC。</li>
     */
    private final Instant updateTime;

    /** 按 JSON 属性绑定，保留字段的可空语义。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public IntegrationApiSummary(
        @JsonProperty("apiCode") String apiCode,
        @JsonProperty("apiName") String apiName,
        @JsonProperty("method") String method,
        @JsonProperty("endpointCode") String endpointCode,
        @JsonProperty("pathTemplate") String pathTemplate,
        @JsonProperty("requestScriptCount") int requestScriptCount,
        @JsonProperty("responseScriptCount") int responseScriptCount,
        @JsonProperty("referencedBy") List<String> referencedBy,
        @JsonProperty("version") int version,
        @JsonProperty("updateTime") Instant updateTime) {
        this.apiCode = apiCode;
        this.apiName = apiName;
        this.method = method;
        this.endpointCode = endpointCode;
        this.pathTemplate = pathTemplate;
        this.requestScriptCount = requestScriptCount;
        this.responseScriptCount = responseScriptCount;
        this.referencedBy = referencedBy == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(referencedBy));
        this.version = version;
        this.updateTime = updateTime;
    }

    /** 原子 API 编码 */
    public String getApiCode() {
        return apiCode;
    }

    /** 名称 */
    public String getApiName() {
        return apiName;
    }

    /** HTTP 方法 */
    public String getMethod() {
        return method;
    }

    /** 出站地址代码 */
    public String getEndpointCode() {
        return endpointCode;
    }

    /** 路径模板 */
    public String getPathTemplate() {
        return pathTemplate;
    }

    /** 请求阶段脚本数 */
    public int getRequestScriptCount() {
        return requestScriptCount;
    }

    /** 响应阶段脚本数 */
    public int getResponseScriptCount() {
        return responseScriptCount;
    }

    /** 引用该 API 的标准 API 编码 */
    public List<String> getReferencedBy() {
        return referencedBy;
    }

    /** 当前版本 */
    public int getVersion() {
        return version;
    }

    /** 最后修改时间 */
    public Instant getUpdateTime() {
        return updateTime;
    }
}
