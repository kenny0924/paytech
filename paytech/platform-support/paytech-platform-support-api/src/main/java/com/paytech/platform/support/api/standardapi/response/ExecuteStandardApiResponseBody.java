package com.paytech.platform.support.api.standardapi.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.databind.JsonNode;

/** 执行标准 API 的结果；不返回节点轨迹、原始报文或初始化结果。 */
public class ExecuteStandardApiResponseBody {

    /**
     * 执行状态
     * <li>SUCCESS、FAIL、ERROR，由流程出口决定；ERROR 不能据此判定渠道未执行。</li>
     */
    private final String executeStatus;

    /**
     * 标准 API 输出
     * <li>按标准 API 响应字段返回；ERROR 时为空对象。</li>
     */
    private final JsonNode outputs;

    /** 按 JSON 属性绑定。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public ExecuteStandardApiResponseBody(@JsonProperty("executeStatus") String executeStatus,
        @JsonProperty("outputs") JsonNode outputs) {
        this.executeStatus = executeStatus;
        this.outputs = outputs;
    }

    /** 执行状态 */
    public String getExecuteStatus() {
        return executeStatus;
    }

    /** 标准 API 输出 */
    public JsonNode getOutputs() {
        return outputs;
    }
}
