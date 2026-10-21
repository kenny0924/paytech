package com.paytech.reconciliation.domain.view;

import com.paytech.reconciliation.core.dal.dataobject.ChannelStatement;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatementLine;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccount;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 渠道账单行与所属账单、清算账户（跨账单查询的一项）。 */
@Getter
@AllArgsConstructor
public class ChannelStatementLineView {
    /** 账单行 */
    private final ChannelStatementLine line;

    /** 所属渠道账单 */
    private final ChannelStatement statement;

    /** 所属清算账户 */
    private final ClearingAccount clearingAccount;
}
