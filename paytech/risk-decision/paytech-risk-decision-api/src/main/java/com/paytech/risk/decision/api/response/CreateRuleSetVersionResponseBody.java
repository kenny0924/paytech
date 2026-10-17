package com.paytech.risk.api.decision.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 规则集管理响应。 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public class CreateRuleSetVersionResponseBody {

    /** 准确规则集版本 ID。 */
    private String ruleSetVersionId;

    /** 已有旧顺序编排的准确 ID；新配置为 null，JSON 键始终保留。 */
    private String pointVersionId;

    /** 规则集编码。 */
    private String ruleSetCode;

    /** 业务版本号。 */
    private Integer versionNo;

    /** 当前状态。 */
    private String status;

    /** 版本内容 Hash。 */
    private String contentHash;

    /** 创建操作者 ID。 */
    private String createdBy;

    /** UTC 微秒创建时间。 */
    private String createTime;

    /** JSON 反序列化构造。 */
    public CreateRuleSetVersionResponseBody() { }

    /** 按完整合同构造。 */
    public CreateRuleSetVersionResponseBody(String ruleSetVersionId, String pointVersionId, String ruleSetCode, Integer versionNo, String status, String contentHash, String createdBy, String createTime) {
        setRuleSetVersionId(ruleSetVersionId);
        setPointVersionId(pointVersionId);
        setRuleSetCode(ruleSetCode);
        setVersionNo(versionNo);
        setStatus(status);
        setContentHash(contentHash);
        setCreatedBy(createdBy);
        setCreateTime(createTime);
    }

    /** 返回准确规则集版本 ID。 */
    public String getRuleSetVersionId() { return ruleSetVersionId; }

    /** 设置准确规则集版本 ID。 */
    public void setRuleSetVersionId(String ruleSetVersionId) {
        this.ruleSetVersionId = ruleSetVersionId;
    }

    /** 返回已有旧顺序编排的准确 ID；新配置为 null，JSON 键始终保留。 */
    public String getPointVersionId() { return pointVersionId; }

    /** 设置已有旧顺序编排的准确 ID；新配置为 null，JSON 键始终保留。 */
    public void setPointVersionId(String pointVersionId) {
        this.pointVersionId = pointVersionId;
    }

    /** 返回规则集编码。 */
    public String getRuleSetCode() { return ruleSetCode; }

    /** 设置规则集编码。 */
    public void setRuleSetCode(String ruleSetCode) {
        this.ruleSetCode = ruleSetCode;
    }

    /** 返回业务版本号。 */
    public Integer getVersionNo() { return versionNo; }

    /** 设置业务版本号。 */
    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    /** 返回当前状态。 */
    public String getStatus() { return status; }

    /** 设置当前状态。 */
    public void setStatus(String status) {
        this.status = status;
    }

    /** 返回版本内容 Hash。 */
    public String getContentHash() { return contentHash; }

    /** 设置版本内容 Hash。 */
    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    /** 返回创建操作者 ID。 */
    public String getCreatedBy() { return createdBy; }

    /** 设置创建操作者 ID。 */
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    /** 返回UTC 微秒创建时间。 */
    public String getCreateTime() { return createTime; }

    /** 设置UTC 微秒创建时间。 */
    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }
}
