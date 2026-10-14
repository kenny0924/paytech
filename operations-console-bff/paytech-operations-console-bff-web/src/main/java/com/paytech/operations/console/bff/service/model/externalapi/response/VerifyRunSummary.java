package com.paytech.operations.console.bff.service.model.externalapi.response;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 验证运行列表项。 */
public class VerifyRunSummary {

    /** 运行编号 */
    private final String runCode;

    /** 用例编码 */
    private final String caseCode;

    /**
     * 用例名称
     * <li>取自运行时快照。</li>
     */
    private final String caseName;

    /** 运行状态 */
    private final String runStatus;

    /** 失败摘要 */
    private final String failureSummary;

    /** 发起账号 */
    private final String startedBy;

    /** 开始时间 */
    private final Instant startTime;

    /** 结束时间 */
    private final Instant finishTime;

    /** 按 JSON 属性绑定，保留字段的可空语义。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public VerifyRunSummary(
        @JsonProperty("runCode") String runCode,
        @JsonProperty("caseCode") String caseCode,
        @JsonProperty("caseName") String caseName,
        @JsonProperty("runStatus") String runStatus,
        @JsonProperty("failureSummary") String failureSummary,
        @JsonProperty("startedBy") String startedBy,
        @JsonProperty("startTime") Instant startTime,
        @JsonProperty("finishTime") Instant finishTime) {
        this.runCode = runCode;
        this.caseCode = caseCode;
        this.caseName = caseName;
        this.runStatus = runStatus;
        this.failureSummary = failureSummary;
        this.startedBy = startedBy;
        this.startTime = startTime;
        this.finishTime = finishTime;
    }

    /** 运行编号 */
    public String getRunCode() {
        return runCode;
    }

    /** 用例编码 */
    public String getCaseCode() {
        return caseCode;
    }

    /** 用例名称 */
    public String getCaseName() {
        return caseName;
    }

    /** 运行状态 */
    public String getRunStatus() {
        return runStatus;
    }

    /** 失败摘要 */
    public String getFailureSummary() {
        return failureSummary;
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
}
