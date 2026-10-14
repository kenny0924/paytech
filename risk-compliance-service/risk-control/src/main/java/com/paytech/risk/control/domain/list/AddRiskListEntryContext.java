package com.paytech.risk.control.domain.list;

import com.paytech.risk.control.core.dal.dataobject.RiskList;
import com.paytech.risk.control.core.dal.dataobject.RiskListEntry;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 添加名单成员：已校验的名单、待插入成员，及需先结束的已过期占位成员。 */
@Getter
@AllArgsConstructor
public class AddRiskListEntryContext {
    /** 所属名单，状态为 ACTIVE。 */
    private final RiskList list;

    /** 待插入的新成员。 */
    private final RiskListEntry entry;

    /** 仍占用生效槽位但已到期的旧成员；没有时为空。 */
    private final RiskListEntry expiredEntry;
}
