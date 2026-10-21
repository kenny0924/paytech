package com.paytech.channel.connector.api.externalapi.response;

import tools.jackson.databind.JsonNode;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 验证运行中的一个步骤。 */
public class VerifyRunStepDetail {

    /** 步骤序号 */
    private final Integer stepNo;

    /** 步骤 ID */
    private final String stepKey;

    /** 标准 API */
    private final String standardApiCode;

    /**
     * 步骤结论
     * <li>PASSED、FAILED、SKIPPED。</li>
     */
    private final String stepStatus;

    /** 实际输入 */
    private final JsonNode resolvedInput;

    /** 实际结果 */
    private final JsonNode result;

    /** 期望核对 */
    private final JsonNode assertions;

    /**
     * 执行轨迹
     * <li>含脱敏后的渠道请求与响应原文。</li>
     */
    private final JsonNode trace;

    /** 耗时毫秒 */
    private final Integer elapsedMs;

    /** 按 JSON 属性绑定，保留字段的可空语义。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public VerifyRunStepDetail(
        @JsonProperty("stepNo") Integer stepNo,
        @JsonProperty("stepKey") String stepKey,
        @JsonProperty("standardApiCode") String standardApiCode,
        @JsonProperty("stepStatus") String stepStatus,
        @JsonProperty("resolvedInput") JsonNode resolvedInput,
        @JsonProperty("result") JsonNode result,
        @JsonProperty("assertions") JsonNode assertions,
        @JsonProperty("trace") JsonNode trace,
        @JsonProperty("elapsedMs") Integer elapsedMs) {
        this.stepNo = stepNo;
        this.stepKey = stepKey;
        this.standardApiCode = standardApiCode;
        this.stepStatus = stepStatus;
        this.resolvedInput = resolvedInput;
        this.result = result;
        this.assertions = assertions;
        this.trace = trace;
        this.elapsedMs = elapsedMs;
    }

    /** 步骤序号 */
    public Integer getStepNo() {
        return stepNo;
    }

    /** 步骤 ID */
    public String getStepKey() {
        return stepKey;
    }

    /** 标准 API */
    public String getStandardApiCode() {
        return standardApiCode;
    }

    /** 步骤结论 */
    public String getStepStatus() {
        return stepStatus;
    }

    /** 实际输入 */
    public JsonNode getResolvedInput() {
        return resolvedInput;
    }

    /** 实际结果 */
    public JsonNode getResult() {
        return result;
    }

    /** 期望核对 */
    public JsonNode getAssertions() {
        return assertions;
    }

    /** 执行轨迹 */
    public JsonNode getTrace() {
        return trace;
    }

    /** 耗时毫秒 */
    public Integer getElapsedMs() {
        return elapsedMs;
    }
}
