package com.paytech.risk.decision.api.control.response;

/** 风险名单定义。 */
public class RiskListResponseBody {

    /** 名单 ID，正整数十进制字符串。 */
    private String listId;

    /** 名单编码。 */
    private String listCode;

    /** 名单名称。 */
    private String listName;

    /** 主体类型 Code。 */
    private String subjectType;

    /** 名单用途 Code。 */
    private String purpose;

    /** 名单是否启用。 */
    private boolean active;

    /** 读取名单 ID。 */
    public String getListId() { return listId; }

    /** 设置名单 ID。 */
    public void setListId(String listId) { this.listId = listId; }

    /** 读取名单编码。 */
    public String getListCode() { return listCode; }

    /** 设置名单编码。 */
    public void setListCode(String listCode) { this.listCode = listCode; }

    /** 读取名单名称。 */
    public String getListName() { return listName; }

    /** 设置名单名称。 */
    public void setListName(String listName) { this.listName = listName; }

    /** 读取主体类型 Code。 */
    public String getSubjectType() { return subjectType; }

    /** 设置主体类型 Code。 */
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }

    /** 读取名单用途 Code。 */
    public String getPurpose() { return purpose; }

    /** 设置名单用途 Code。 */
    public void setPurpose(String purpose) { this.purpose = purpose; }

    /** 读取名单是否启用。 */
    public boolean getActive() { return active; }

    /** 设置名单是否启用。 */
    public void setActive(boolean active) { this.active = active; }
}
