package com.paytech.risk.decision.api.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.paytech.risk.decision.api.DecisionReleaseManagementModels.*;

/** TEST Candidate 的受控协议结构。 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public class CreateTestReleaseResponseBody {

    /** Candidate 根 ID，以正 BIGINT 十进制字符串传输。 */
    private String releaseId;

    /** 全局发布编号，正整数，最大为 2147483647。 */
    private Integer releaseNo;

    /** 准确决策点 ID，以十进制字符串传输。 */
    private String decisionPointId;

    /** Risk 决策点编码，本期为 CHECKOUT_PREPARE。 */
    private String pointCode;

    /** 旧发布组成中的准确 Point Version ID。 */
    private String pointVersionId;

    /** 准确冻结规则版本 ID，以正 BIGINT 十进制字符串传输。 */
    private String ruleSetVersionId;

    /** 发布环境，首期固定 TEST。 */
    private String environmentCode;

    /** Candidate 状态，DRAFT、VALIDATING 或 REJECTED。 */
    private String status;

    /** Candidate 当前聚合版本。 */
    private Integer version;

    /** 准确冻结组成的内容 SHA-256。 */
    private String contentHash;

    /** 创建人的 Account ID，以十进制字符串传输。 */
    private String createdBy;

    /** 创建时刻，UTC 六位微秒。 */
    private String createTime;

    /** 本次创建的初始 DRAFT 审计事实。 */
    private InitialStateChangeResponseBody initialStateChange;

    /** 供普通 JSON 属性绑定使用。 */
    public CreateTestReleaseResponseBody() { }

    /** 根据完整协议字段构造。 */
    public CreateTestReleaseResponseBody(String releaseId, Integer releaseNo, String decisionPointId, String pointCode, String pointVersionId, String ruleSetVersionId, String environmentCode, String status, Integer version, String contentHash, String createdBy, String createTime, InitialStateChangeResponseBody initialStateChange) {
        this.releaseId = releaseId;
        this.releaseNo = releaseNo;
        this.decisionPointId = decisionPointId;
        this.pointCode = pointCode;
        this.pointVersionId = pointVersionId;
        this.ruleSetVersionId = ruleSetVersionId;
        this.environmentCode = environmentCode;
        this.status = status;
        this.version = version;
        this.contentHash = contentHash;
        this.createdBy = createdBy;
        this.createTime = createTime;
        this.initialStateChange = initialStateChange;
    }

    /** 返回Candidate 根 ID，以正 BIGINT 十进制字符串传输。 */
    public String getReleaseId() { return releaseId; }

    /** 设置Candidate 根 ID，以正 BIGINT 十进制字符串传输。 */
    public void setReleaseId(String releaseId) { this.releaseId = releaseId; }

    /** 返回全局发布编号，正整数，最大为 2147483647。 */
    public Integer getReleaseNo() { return releaseNo; }

    /** 设置全局发布编号，正整数，最大为 2147483647。 */
    public void setReleaseNo(Integer releaseNo) { this.releaseNo = releaseNo; }

    /** 返回准确决策点 ID，以十进制字符串传输。 */
    public String getDecisionPointId() { return decisionPointId; }

    /** 设置准确决策点 ID，以十进制字符串传输。 */
    public void setDecisionPointId(String decisionPointId) { this.decisionPointId = decisionPointId; }

    /** 返回Risk 决策点编码，本期为 CHECKOUT_PREPARE。 */
    public String getPointCode() { return pointCode; }

    /** 设置Risk 决策点编码，本期为 CHECKOUT_PREPARE。 */
    public void setPointCode(String pointCode) { this.pointCode = pointCode; }

    /** 返回旧发布组成中的准确 Point Version ID。 */
    public String getPointVersionId() { return pointVersionId; }

    /** 设置旧发布组成中的准确 Point Version ID。 */
    public void setPointVersionId(String pointVersionId) { this.pointVersionId = pointVersionId; }

    /** 返回准确冻结规则版本 ID，以正 BIGINT 十进制字符串传输。 */
    public String getRuleSetVersionId() { return ruleSetVersionId; }

    /** 设置准确冻结规则版本 ID，以正 BIGINT 十进制字符串传输。 */
    public void setRuleSetVersionId(String ruleSetVersionId) { this.ruleSetVersionId = ruleSetVersionId; }

    /** 返回发布环境，首期固定 TEST。 */
    public String getEnvironmentCode() { return environmentCode; }

    /** 设置发布环境，首期固定 TEST。 */
    public void setEnvironmentCode(String environmentCode) { this.environmentCode = environmentCode; }

    /** 返回Candidate 状态，DRAFT、VALIDATING 或 REJECTED。 */
    public String getStatus() { return status; }

    /** 设置Candidate 状态，DRAFT、VALIDATING 或 REJECTED。 */
    public void setStatus(String status) { this.status = status; }

    /** 返回Candidate 当前聚合版本。 */
    public Integer getVersion() { return version; }

    /** 设置Candidate 当前聚合版本。 */
    public void setVersion(Integer version) { this.version = version; }

    /** 返回准确冻结组成的内容 SHA-256。 */
    public String getContentHash() { return contentHash; }

    /** 设置准确冻结组成的内容 SHA-256。 */
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }

    /** 返回创建人的 Account ID，以十进制字符串传输。 */
    public String getCreatedBy() { return createdBy; }

    /** 设置创建人的 Account ID，以十进制字符串传输。 */
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    /** 返回创建时刻，UTC 六位微秒。 */
    public String getCreateTime() { return createTime; }

    /** 设置创建时刻，UTC 六位微秒。 */
    public void setCreateTime(String createTime) { this.createTime = createTime; }

    /** 返回本次创建的初始 DRAFT 审计事实。 */
    public InitialStateChangeResponseBody getInitialStateChange() { return initialStateChange; }

    /** 设置本次创建的初始 DRAFT 审计事实。 */
    public void setInitialStateChange(InitialStateChangeResponseBody initialStateChange) { this.initialStateChange = initialStateChange; }
}
