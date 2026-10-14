package com.paytech.risk.control.domain.list;

import com.paytech.risk.control.core.dal.dataobject.RiskList;
import com.paytech.risk.control.core.enums.RiskListStatus;

/**
 * 名单定义的跨模块只读结果
 *
 * @param listId 名单 ID
 * @param listCode 名单编码
 * @param listName 名单名称
 * @param subjectType 名单支持的主体类型
 * @param purpose 名单用途 Code
 * @param active 名单是否启用
 */
public record RiskListView(long listId, String listCode, String listName, RiskSubjectType subjectType, String purpose,
    boolean active) {
    /** 由名单定义构造只读结果。 */
    public static RiskListView of(RiskList list) {
        return new RiskListView(list.getListId(), list.getListCode(), list.getListName(), list.getSupportedSubjectType(),
            list.getListPurpose().getCode(), list.getStatus() == RiskListStatus.ACTIVE);
    }
}
