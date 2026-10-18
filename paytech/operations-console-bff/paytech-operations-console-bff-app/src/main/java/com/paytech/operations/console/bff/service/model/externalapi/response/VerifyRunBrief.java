package com.paytech.operations.console.bff.service.model.externalapi.response;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 用例最近一次运行的摘要。 */
public class VerifyRunBrief {

    /** 运行编号 */
    private final String runCode;

    /**
     * 运行状态
     * <li>RUNNING、PASSED、FAILED、INTERRUPTED。</li>
     */
    private final String runStatus;

    /** 开始时间 */
    private final Instant startTime;

    /** 结束时间 */
    private final Instant finishTime;

    /** 按 JSON 属性绑定，保留字段的可空语义。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public VerifyRunBrief(
        @JsonProperty("runCode") String runCode,
        @JsonProperty("runStatus") String runStatus,
        @JsonProperty("startTime") Instant startTime,
        @JsonProperty("finishTime") Instant finishTime) {
        this.runCode = runCode;
        this.runStatus = runStatus;
        this.startTime = startTime;
        this.finishTime = finishTime;
    }

    /** 运行编号 */
    public String getRunCode() {
        return runCode;
    }

    /** 运行状态 */
    public String getRunStatus() {
        return runStatus;
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
