package com.paytech.ledger.domain;

import com.paytech.ledger.core.dal.dataobject.ChartAccount;
import com.paytech.ledger.core.dal.dataobject.ChartAccountChange;
import com.paytech.ledger.core.dal.dataobject.ChartOfAccounts;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 一次科目维护：锁内已校验的科目表、目标科目、上级与待追加的变更记录
 * <li>CREATE 时目标科目是待插入的新对象；其余类型是已持久化对象，保存成功后由 Repository 同步为变更后的值。</li>
 * <li>变更后的编号、上级、名称与状态以变更记录的 after 字段为准。</li>
 */
@Getter
@AllArgsConstructor
public class ChartAccountChangeContext {
    /** 路径指定且 ACTIVE 的科目表。 */
    private final ChartOfAccounts chart;

    /** 被维护的科目。 */
    private final ChartAccount account;

    /** 变更后的直接上级；一级科目为空。 */
    private final ChartAccount parent;

    /** 本次追加的变更记录。 */
    private final ChartAccountChange change;
}
