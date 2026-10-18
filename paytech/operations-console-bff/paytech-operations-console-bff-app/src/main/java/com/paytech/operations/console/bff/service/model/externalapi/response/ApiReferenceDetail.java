package com.paytech.operations.console.bff.service.model.externalapi.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 引用原子 API 的标准 API 实现及其节点。 */
public class ApiReferenceDetail {

    /** 标准 API 编码 */
    private final String standardApiCode;

    /** 流程中引用该 API 的节点标识 */
    private final List<String> nodeIds;

    /** 按 JSON 属性绑定，保留字段的可空语义。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public ApiReferenceDetail(
        @JsonProperty("standardApiCode") String standardApiCode,
        @JsonProperty("nodeIds") List<String> nodeIds) {
        this.standardApiCode = standardApiCode;
        this.nodeIds = nodeIds == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(nodeIds));
    }

    /** 标准 API 编码 */
    public String getStandardApiCode() {
        return standardApiCode;
    }

    /** 流程中引用该 API 的节点标识 */
    public List<String> getNodeIds() {
        return nodeIds;
    }
}
