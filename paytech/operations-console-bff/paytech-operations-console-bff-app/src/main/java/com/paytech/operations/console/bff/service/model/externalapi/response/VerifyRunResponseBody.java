package com.paytech.operations.console.bff.service.model.externalapi.response;

import java.time.Instant;
import java.util.List;

import tools.jackson.databind.JsonNode;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 验证运行详情与证据。 */
public class VerifyRunResponseBody {

    /** 运行编号 */
    private final String runCode;

    /** 用例编码 */
    private final String caseCode;

    /** 运行状态 */
    private final String runStatus;

    /** 失败摘要 */
    private final String failureSummary;

    /** 运行时的用例快照 */
    private final JsonNode caseSnapshot;

    /** 运行时的配置版本 */
    private final JsonNode configVersions;

    /** 运行时的出站地址 */
    private final JsonNode endpoints;

    /**
     * 是否已过期
     * <li>用例、账号或引用配置在运行后已变更。</li>
     */
    private final boolean stale;

    /** 发起账号 */
    private final String startedBy;

    /** 开始时间 */
    private final Instant startTime;

    /** 结束时间 */
    private final Instant finishTime;

    /** 步骤 */
    private final List<VerifyRunStepDetail> steps;

    /** 按 JSON 属性绑定，保留字段的可空语义。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public VerifyRunResponseBody(
        @JsonProperty("runCode") String runCode,
        @JsonProperty("caseCode") String caseCode,
        @JsonProperty("runStatus") String runStatus,
        @JsonProperty("failureSummary") String failureSummary,
        @JsonProperty("caseSnapshot") JsonNode caseSnapshot,
        @JsonProperty("configVersions") JsonNode configVersions,
        @JsonProperty("endpoints") JsonNode endpoints,
        @JsonProperty("stale") boolean stale,
        @JsonProperty("startedBy") String startedBy,
        @JsonProperty("startTime") Instant startTime,
        @JsonProperty("finishTime") Instant finishTime,
        @JsonProperty("steps") List<VerifyRunStepDetail> steps) {
        this.runCode = runCode;
        this.caseCode = caseCode;
        this.runStatus = runStatus;
        this.failureSummary = failureSummary;
        this.caseSnapshot = caseSnapshot;
        this.configVersions = configVersions;
        this.endpoints = endpoints;
        this.stale = stale;
        this.startedBy = startedBy;
        this.startTime = startTime;
        this.finishTime = finishTime;
        this.steps = steps == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(steps));
    }

    /** 运行编号 */
    public String getRunCode() {
        return runCode;
    }

    /** 用例编码 */
    public String getCaseCode() {
        return caseCode;
    }

    /** 运行状态 */
    public String getRunStatus() {
        return runStatus;
    }

    /** 失败摘要 */
    public String getFailureSummary() {
        return failureSummary;
    }

    /** 运行时的用例快照 */
    public JsonNode getCaseSnapshot() {
        return caseSnapshot;
    }

    /** 运行时的配置版本 */
    public JsonNode getConfigVersions() {
        return configVersions;
    }

    /** 运行时的出站地址 */
    public JsonNode getEndpoints() {
        return endpoints;
    }

    /** 是否已过期 */
    public boolean isStale() {
        return stale;
    }

    /** 发起账号 */
    public String getStartedBy() {
        return startedBy;
    }

    /** 开始时间 */
    public Instant getStartTime() {
        return startTime;
    }

    /** 结束时间 */
    public Instant getFinishTime() {
        return finishTime;
    }

    /** 步骤 */
    public List<VerifyRunStepDetail> getSteps() {
        return steps;
    }
}
