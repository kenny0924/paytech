package com.paytech.platform.support.api.externalapi.response;

import java.time.Instant;

import tools.jackson.databind.JsonNode;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 验证用例详情。 */
public class VerifyCaseResponseBody {

    /** 用例编码 */
    private final String caseCode;

    /** 名称 */
    private final String caseName;

    /** 说明 */
    private final String description;

    /** 验证账号编码 */
    private final String accountCode;

    /** 步骤 */
    private final JsonNode steps;

    /**
     * 验证状态
     * <li>NOT_RUN、RUNNING、PASSED、FAILED、STALE。</li>
     */
    private final String verifyStatus;

    /** 最近一次运行 */
    private final VerifyRunBrief latestRun;

    /** 版本 */
    private final Integer version;

    /** 创建时间 */
    private final Instant createTime;

    /** 最后修改时间 */
    private final Instant updateTime;

    /** 最后修改账号 */
    private final String updatedBy;

    /** 按 JSON 属性绑定，保留字段的可空语义。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public VerifyCaseResponseBody(
        @JsonProperty("caseCode") String caseCode,
        @JsonProperty("caseName") String caseName,
        @JsonProperty("description") String description,
        @JsonProperty("accountCode") String accountCode,
        @JsonProperty("steps") JsonNode steps,
        @JsonProperty("verifyStatus") String verifyStatus,
        @JsonProperty("latestRun") VerifyRunBrief latestRun,
        @JsonProperty("version") Integer version,
        @JsonProperty("createTime") Instant createTime,
        @JsonProperty("updateTime") Instant updateTime,
        @JsonProperty("updatedBy") String updatedBy) {
        this.caseCode = caseCode;
        this.caseName = caseName;
        this.description = description;
        this.accountCode = accountCode;
        this.steps = steps;
        this.verifyStatus = verifyStatus;
        this.latestRun = latestRun;
        this.version = version;
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.updatedBy = updatedBy;
    }

    /** 用例编码 */
    public String getCaseCode() {
        return caseCode;
    }

    /** 名称 */
    public String getCaseName() {
        return caseName;
    }

    /** 说明 */
    public String getDescription() {
        return description;
    }

    /** 验证账号编码 */
    public String getAccountCode() {
        return accountCode;
    }

    /** 步骤 */
    public JsonNode getSteps() {
        return steps;
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

    /** 创建时间 */
    public Instant getCreateTime() {
        return createTime;
    }

    /** 最后修改时间 */
    public Instant getUpdateTime() {
        return updateTime;
    }

    /** 最后修改账号 */
    public String getUpdatedBy() {
        return updatedBy;
    }
}
