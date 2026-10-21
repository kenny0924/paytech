package com.paytech.reconciliation.domain.view;

import java.util.List;

import com.paytech.reconciliation.core.dal.dataobject.ClearingAccount;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccountMid;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 清算账户详情：账户与按挂入时间升序的 MID 归属行（两表组合）。 */
@Getter
@AllArgsConstructor
public class ClearingAccountView {
    /** 清算账户 */
    private final ClearingAccount account;

    /** 已挂入的 MID */
    private final List<ClearingAccountMid> mids;
}
