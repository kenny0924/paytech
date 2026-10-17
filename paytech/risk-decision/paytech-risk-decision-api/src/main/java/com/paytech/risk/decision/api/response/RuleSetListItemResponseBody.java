package com.paytech.risk.api.decision.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 规则集管理响应。 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public class RuleSetListItemResponseBody {

    /** 准确规则集版本 ID。 */
    private String ruleSetVersionId;

    /** 已有旧顺序编排的准确 ID；新配置为 null，JSON 键始终保留。 */
    private String pointVersionId;

    /** 决策事件点编码。 */
    private String pointCode;

    /** 规则集编码。 */
    private String ruleSetCode;

    /** 规则集名称。 */
    private String ruleSetName;

    /** 业务版本号。 */
    private Integer versionNo;

    /** 当前状态。 */
    private String status;

    /** 版本内容 Hash。 */
    private String contentHash;

    /** 创建操作者 ID。 */
    private String createdBy;

    /** 本次变更原因。 */
    private String changeReason;

    /** UTC 微秒创建时间。 */
    private String createTime;

    /** 冻结操作者 ID；未冻结为 null。 */
    private String frozenBy;

    /** UTC 微秒冻结时间；未冻结为 null。 */
    private String frozenTime;

    /** 整体管理身份，删除针对此 ID 而不是版本 ID。 */
    private String ruleSetId;

    /** 根管理 CAS 版本的正十进制文本，和 versionNo 独立。 */
    private String managementVersion;

    /** 任一历史版本被当前实际生效发布使用；不代表用户权限。 */
    private Boolean inUse;

    /** JSON 反序列化构造。 */
    public RuleSetListItemResponseBody() { }

    /** 按完整合同构造。 */
    public RuleSetListItemResponseBody(String ruleSetVersionId, String pointVersionId, String pointCode, String ruleSetCode, String ruleSetName, Integer versionNo, String status, String contentHash, String createdBy, String changeReason, String createTime, String frozenBy, String frozenTime, String ruleSetId, String managementVersion, Boolean inUse) {
        setRuleSetVersionId(ruleSetVersionId);
        setPointVersionId(pointVersionId);
        setPointCode(pointCode);
        setRuleSetCode(ruleSetCode);
        setRuleSetName(ruleSetName);
        setVersionNo(versionNo);
        setStatus(status);
        setContentHash(contentHash);
        setCreatedBy(createdBy);
        setChangeReason(changeReason);
        setCreateTime(createTime);
        setFrozenBy(frozenBy);
        setFrozenTime(frozenTime);
        setRuleSetId(ruleSetId);
        setManagementVersion(managementVersion);
        setInUse(inUse);
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

    /** 返回决策事件点编码。 */
    public String getPointCode() { return pointCode; }

    /** 设置决策事件点编码。 */
    public void setPointCode(String pointCode) {
        this.pointCode = pointCode;
    }

    /** 返回规则集编码。 */
    public String getRuleSetCode() { return ruleSetCode; }

    /** 设置规则集编码。 */
    public void setRuleSetCode(String ruleSetCode) {
        this.ruleSetCode = ruleSetCode;
    }

    /** 返回规则集名称。 */
    public String getRuleSetName() { return ruleSetName; }

    /** 设置规则集名称。 */
    public void setRuleSetName(String ruleSetName) {
        this.ruleSetName = ruleSetName;
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

    /** 返回本次变更原因。 */
    public String getChangeReason() { return changeReason; }

    /** 设置本次变更原因。 */
    public void setChangeReason(String changeReason) {
        this.changeReason = changeReason;
    }

    /** 返回UTC 微秒创建时间。 */
    public String getCreateTime() { return createTime; }

    /** 设置UTC 微秒创建时间。 */
    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    /** 返回冻结操作者 ID；未冻结为 null。 */
    public String getFrozenBy() { return frozenBy; }

    /** 设置冻结操作者 ID；未冻结为 null。 */
    public void setFrozenBy(String frozenBy) {
        this.frozenBy = frozenBy;
    }

    /** 返回UTC 微秒冻结时间；未冻结为 null。 */
    public String getFrozenTime() { return frozenTime; }

    /** 设置UTC 微秒冻结时间；未冻结为 null。 */
    public void setFrozenTime(String frozenTime) {
        this.frozenTime = frozenTime;
    }

    /** 返回整体管理身份，删除针对此 ID 而不是版本 ID。 */
    public String getRuleSetId() { return ruleSetId; }

    /** 设置整体管理身份，删除针对此 ID 而不是版本 ID。 */
    public void setRuleSetId(String ruleSetId) {
        this.ruleSetId = ruleSetId;
    }

    /** 返回根管理 CAS 版本的正十进制文本，和 versionNo 独立。 */
    public String getManagementVersion() { return managementVersion; }

    /** 设置根管理 CAS 版本的正十进制文本，和 versionNo 独立。 */
    public void setManagementVersion(String managementVersion) {
        this.managementVersion = managementVersion;
    }

    /** 返回任一历史版本被当前实际生效发布使用；不代表用户权限。 */
    public Boolean getInUse() { return inUse; }

    /** 设置任一历史版本被当前实际生效发布使用；不代表用户权限。 */
    public void setInUse(Boolean inUse) {
        this.inUse = inUse;
    }
}
