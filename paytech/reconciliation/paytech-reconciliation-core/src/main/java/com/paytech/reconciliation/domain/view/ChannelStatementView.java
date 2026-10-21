package com.paytech.reconciliation.domain.view;

import com.paytech.reconciliation.core.dal.dataobject.ChannelStatement;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccount;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 渠道账单与所属清算账户（响应带清算账户编码）。 */
@Getter
@AllArgsConstructor
public class ChannelStatementView {
    /** 渠道账单 */
    private final ChannelStatement statement;

    /** 所属清算账户 */
    private final ClearingAccount clearingAccount;
}
