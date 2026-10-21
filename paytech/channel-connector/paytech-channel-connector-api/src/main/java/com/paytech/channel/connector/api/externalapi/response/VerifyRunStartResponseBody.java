package com.paytech.channel.connector.api.externalapi.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 已发起的验证运行。 */
public class VerifyRunStartResponseBody {

    /** 运行编号 */
    private final String runCode;

    /**
     * 运行状态
     * <li>发起时为 RUNNING。</li>
     */
    private final String runStatus;

    /** 按 JSON 属性绑定，保留字段的可空语义。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public VerifyRunStartResponseBody(
        @JsonProperty("runCode") String runCode,
        @JsonProperty("runStatus") String runStatus) {
        this.runCode = runCode;
        this.runStatus = runStatus;
    }

    /** 运行编号 */
    public String getRunCode() {
        return runCode;
    }

    /** 运行状态 */
    public String getRunStatus() {
        return runStatus;
    }
}
