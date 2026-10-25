package com.paytech.compliance.api.response;

import java.util.List;

/** 一个角色资格的状态变更历史，按资格版本倒序。 */
public class QualificationHistoryResponseBody {

    /**
     * 角色资格 ID
     * <li>十进制字符串。</li>
     */
    private String qualificationId;

    /** 状态变更，新的在前 */
    private List<QualificationHistoryItemBody> items;

    /** 读取角色资格 ID。 */
    public String getQualificationId() {
        return qualificationId;
    }

    /** 设置角色资格 ID。 */
    public void setQualificationId(String qualificationId) {
        this.qualificationId = qualificationId;
    }

    /** 读取状态变更，新的在前。 */
    public List<QualificationHistoryItemBody> getItems() {
        return items;
    }

    /** 设置状态变更，新的在前。 */
    public void setItems(List<QualificationHistoryItemBody> items) {
        this.items = items;
    }
}
