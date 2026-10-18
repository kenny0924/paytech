package com.paytech.operations.console.bff.service.model.externalapi.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 流程编辑器一次取得的上下文。 */
public class FlowEditorContextResponseBody {

    /** 被实现的标准 API 字段 */
    private final FlowStandardApiDetail standardApi;

    /**
     * 本接入方全部原子 API 合同
     * <li>按编码排序。</li>
     */
    private final List<FlowApiContractDetail> apis;

    /** 按 JSON 属性绑定，保留字段的可空语义。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public FlowEditorContextResponseBody(
        @JsonProperty("standardApi") FlowStandardApiDetail standardApi,
        @JsonProperty("apis") List<FlowApiContractDetail> apis) {
        this.standardApi = standardApi;
        this.apis = apis == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(apis));
    }

    /** 被实现的标准 API 字段 */
    public FlowStandardApiDetail getStandardApi() {
        return standardApi;
    }

    /** 本接入方全部原子 API 合同 */
    public List<FlowApiContractDetail> getApis() {
        return apis;
    }
}
