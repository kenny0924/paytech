package com.paytech.platform.support.api.externalapi.response;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 验证用例列表项。 */
public class VerifyCaseSummary {

    /** 用例编码 */
    private final String caseCode;

    /** 名称 */
    private final String caseName;

    /** 验证账号编码 */
    private final String accountCode;

    /**
     * 步骤涉及的标准 API
     * <li>按步骤顺序去重。</li>
     */
    private final List<String> standardApiCodes;

    /** 步骤数 */
    private final Integer stepCount;

    /**
     * 验证状态
     * <li>NOT_RUN、RUNNING、PASSED、FAILED、STALE。</li>
     */
    private final String verifyStatus;

    /**
     * 最近一次运行
     * <li>没有运行时为 null。</li>
     */
    private final VerifyRunBrief latestRun;

    /** 版本 */
    private final Integer version;

    /** 最后修改时间 */
    private final Instant updateTime;

    /** 按 JSON 属性绑定，保留字段的可空语义。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public VerifyCaseSummary(
        @JsonProperty("caseCode") String caseCode,
        @JsonProperty("caseName") String caseName,
        @JsonProperty("accountCode") String accountCode,
        @JsonProperty("standardApiCodes") List<String> standardApiCodes,
        @JsonProperty("stepCount") Integer stepCount,
        @JsonProperty("verifyStatus") String verifyStatus,
        @JsonProperty("latestRun") VerifyRunBrief latestRun,
        @JsonProperty("version") Integer version,
        @JsonProperty("updateTime") Instant updateTime) {
        this.caseCode = caseCode;
        this.caseName = caseName;
        this.accountCode = accountCode;
        this.standardApiCodes = standardApiCodes == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(standardApiCodes));
        this.stepCount = stepCount;
        this.verifyStatus = verifyStatus;
        this.latestRun = latestRun;
        this.version = version;
        this.updateTime = updateTime;
    }

    /** 用例编码 */
    public String getCaseCode() {
        return caseCode;
    }

    /** 名称 */
    public String getCaseName() {
        return caseName;
    }

    /** 验证账号编码 */
    public String getAccountCode() {
        return accountCode;
    }

    /** 步骤涉及的标准 API */
    public List<String> getStandardApiCodes() {
        return standardApiCodes;
    }

    /** 步骤数 */
    public Integer getStepCount() {
        return stepCount;
    }

    /** 验证状态 */
    public String getVerifyStatus() {
        return verifyStatus;
    }

    /** 最近一次运行 */
    public VerifyRunBrief getLatestRun() {
        return latestRun;
    }

    /** 版本 */
    public Integer getVersion() {
        return version;
    }

    /** 最后修改时间 */
    public Instant getUpdateTime() {
        return updateTime;
    }
}
