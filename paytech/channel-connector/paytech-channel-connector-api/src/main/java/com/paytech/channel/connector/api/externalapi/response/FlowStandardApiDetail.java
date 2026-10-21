package com.paytech.channel.connector.api.externalapi.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 流程编辑器使用的标准 API 字段。 */
public class FlowStandardApiDetail {

    /**
     * 请求字段
     * <li>流程中以 input.<fieldPath> 引用。</li>
     */
    private final List<ApiFieldDetail> requestFields;

    /**
     * 响应字段
     * <li>出口 outputs 的键。</li>
     */
    private final List<ApiFieldDetail> responseFields;

    /** 按 JSON 属性绑定，保留字段的可空语义。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public FlowStandardApiDetail(
        @JsonProperty("requestFields") List<ApiFieldDetail> requestFields,
        @JsonProperty("responseFields") List<ApiFieldDetail> responseFields) {
        this.requestFields = requestFields == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(requestFields));
        this.responseFields = responseFields == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(responseFields));
    }

    /** 请求字段 */
    public List<ApiFieldDetail> getRequestFields() {
        return requestFields;
    }

    /** 响应字段 */
    public List<ApiFieldDetail> getResponseFields() {
        return responseFields;
    }
}
