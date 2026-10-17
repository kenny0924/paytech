package com.paytech.risk.decision.api.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 规则集管理响应。 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public class DeleteRuleSetResponseBody {

    /** 已删除规则集整体身份。 */
    private String ruleSetId;

    /** 固定为 DELETED，不表示业务版本被物理删除。 */
    private String status;

    /** 删除后的管理版本，正 BIGINT 文本。 */
    private String version;

    /** 执行首次删除的 Operations Account ID。 */
    private String deletedBy;

    /** 首次删除的 UTC 六位微秒时间，删除事实不可改写。 */
    private String deletedTime;

    /** JSON 反序列化构造。 */
    public DeleteRuleSetResponseBody() { }

    /** 按完整合同构造。 */
    public DeleteRuleSetResponseBody(String ruleSetId, String status, String version, String deletedBy, String deletedTime) {
        setRuleSetId(ruleSetId);
        setStatus(status);
        setVersion(version);
        setDeletedBy(deletedBy);
        setDeletedTime(deletedTime);
    }

    /** 返回已删除规则集整体身份。 */
    public String getRuleSetId() { return ruleSetId; }

    /** 设置已删除规则集整体身份。 */
    public void setRuleSetId(String ruleSetId) {
        this.ruleSetId = ruleSetId;
    }

    /** 返回固定为 DELETED，不表示业务版本被物理删除。 */
    public String getStatus() { return status; }

    /** 设置固定为 DELETED，不表示业务版本被物理删除。 */
    public void setStatus(String status) {
        this.status = status;
    }

    /** 返回删除后的管理版本，正 BIGINT 文本。 */
    public String getVersion() { return version; }

    /** 设置删除后的管理版本，正 BIGINT 文本。 */
    public void setVersion(String version) {
        this.version = version;
    }

    /** 返回执行首次删除的 Operations Account ID。 */
    public String getDeletedBy() { return deletedBy; }

    /** 设置执行首次删除的 Operations Account ID。 */
    public void setDeletedBy(String deletedBy) {
        this.deletedBy = deletedBy;
    }

    /** 返回首次删除的 UTC 六位微秒时间，删除事实不可改写。 */
    public String getDeletedTime() { return deletedTime; }

    /** 设置首次删除的 UTC 六位微秒时间，删除事实不可改写。 */
    public void setDeletedTime(String deletedTime) {
        this.deletedTime = deletedTime;
    }
}
