package com.paytech.platform.support.api.externalapi.response;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 流程节点引用原子 API 时依赖的合同摘要。 */
public class FlowApiContractDetail {

    /** 原子 API 编码 */
    private final String apiCode;

    /** 名称 */
    private final String apiName;

    /**
     * 请求字段
     * <li>节点 request 的键为 <fieldLocation>:<fieldPath>。</li>
     */
    private final List<FlowRequestFieldDetail> requestFields;

    /** 调用参数声明 */
    private final List<ScriptParameterDetail> params;

    /**
     * 输出声明
     * <li>键为 SUCCESS、FAIL。</li>
     */
    private final Map<String, List<ScriptParameterDetail>> outputs;

    /** 按 JSON 属性绑定，保留字段的可空语义。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public FlowApiContractDetail(
        @JsonProperty("apiCode") String apiCode,
        @JsonProperty("apiName") String apiName,
        @JsonProperty("requestFields") List<FlowRequestFieldDetail> requestFields,
        @JsonProperty("params") List<ScriptParameterDetail> params,
        @JsonProperty("outputs") Map<String, List<ScriptParameterDetail>> outputs) {
        this.apiCode = apiCode;
        this.apiName = apiName;
        this.requestFields = requestFields == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(requestFields));
        this.params = params == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(params));
        this.outputs = outputs;
    }

    /** 原子 API 编码 */
    public String getApiCode() {
        return apiCode;
    }

    /** 名称 */
    public String getApiName() {
        return apiName;
    }

    /** 请求字段 */
    public List<FlowRequestFieldDetail> getRequestFields() {
        return requestFields;
    }

    /** 调用参数声明 */
    public List<ScriptParameterDetail> getParams() {
        return params;
    }

    /** 输出声明 */
    public Map<String, List<ScriptParameterDetail>> getOutputs() {
        return outputs;
    }
}
